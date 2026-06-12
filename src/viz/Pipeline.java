package viz;

import viz.annotate.Annotator;
import viz.compile.Compiler;
import viz.condense.Condenser;
import viz.driver.*;
import viz.model.*;
import viz.trace.Tracer;
import viz.util.Json;
import java.util.*;

/** Orchestrates analyze → compile → trace → annotate → condense into the Trace contract. */
public final class Pipeline {
    private Pipeline() {}

    public static Map<String, Object> analyze(String code) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Sig> methods = Analyzer.publicMethods(code == null ? "" : code);
        if (methods.isEmpty()) {
            out.put("ok", false);
            out.put("error", "No public method found — expose one public method on class Solution.");
        } else {
            out.put("ok", true);
            out.put("methods", methods);
        }
        return out;
    }

    public static Map<String, Object> trace(String code, List<String> inputs, String methodName) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Sig> methods = Analyzer.publicMethods(code == null ? "" : code);
        if (methods.isEmpty()) {
            out.put("ok", false); out.put("stage", "analyze");
            out.put("error", "No public method found — expose one public method on class Solution.");
            return out;
        }
        Sig sig = methods.get(0);
        if (methodName != null) for (Sig s : methods) if (s.name().equals(methodName)) sig = s;
        if (sig.params().size() != inputs.size()) {
            out.put("ok", false); out.put("stage", "input");
            out.put("error", "expected " + sig.params().size() + " input(s), got " + inputs.size());
            return out;
        }
        List<Inputs.JavaArg> args = new ArrayList<>();
        try {
            for (int i = 0; i < inputs.size(); i++)
                args.add(Inputs.toJava(i, sig.params().get(i).type(), inputs.get(i)));
        } catch (Inputs.InputError e) {
            out.put("ok", false); out.put("stage", "input");
            out.put("param", e.param); out.put("error", e.getMessage());
            return out;
        }
        try {
            Compiler.Result cr = Compiler.compile(code, Driver.generate(sig, args));
            if (cr.classDir() == null) {
                out.put("ok", false); out.put("stage", "compile"); out.put("errors", cr.errors());
                return out;
            }
            Tracer.Run run = Tracer.run(cr.classDir(), sig, cr.normalized().offset());
            List<Trace.Step> steps = withChanged(run.steps());
            Map<String, String> pointers = Annotator.pointers(code, steps);
            List<Trace.Group> groups = Condenser.groups(steps, run.result());
            out.put("ok", true);
            out.put("trace", new Trace(code, sig, inputs, run.result(), pointers, steps, groups,
                    run.console(), run.notice()));
            return out;
        } catch (Exception e) {
            out.put("ok", false); out.put("stage", "trace"); out.put("error", String.valueOf(e));
            return out;
        }
    }

    /** Recompute each step's changed[] by diffing serialized locals against the previous step. */
    static List<Trace.Step> withChanged(List<Trace.Step> raw) {
        List<Trace.Step> out = new ArrayList<>(raw.size());
        Map<String, String> prev = Map.of();
        for (Trace.Step s : raw) {
            Map<String, String> cur = new LinkedHashMap<>();
            for (var e : s.locals().entrySet()) cur.put(e.getKey(), Json.write(e.getValue()));
            List<String> changed = new ArrayList<>();
            for (var e : cur.entrySet()) if (!e.getValue().equals(prev.get(e.getKey()))) changed.add(e.getKey());
            out.add(new Trace.Step(s.i(), s.line(), s.locals(), changed, s.stdout()));
            prev = cur;
        }
        return out;
    }
}
