package viz.trace;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import viz.model.Sig;
import viz.model.Trace;
import java.nio.file.Path;
import java.util.*;

/** Runs __Driver in a child JVM under JDI, stepping line-by-line through class Solution
 *  and snapshotting real state at every step. The honesty core: nothing here guesses. */
public final class Tracer {
    private Tracer() {}

    public static final int STEP_CAP = 2000;
    public static final long TIMEOUT_MS = 10_000;

    public record Run(List<Trace.Step> steps, Trace.Result result, String console, String notice) {}

    public static Run run(Path classDir, Sig sig, int lineOffset) throws Exception {
        LaunchingConnector conn = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> args = conn.defaultArguments();
        args.get("main").setValue("__Driver");
        args.get("options").setValue("-cp \"" + classDir + "\"");
        VirtualMachine vm = conn.launch(args);

        StringBuilder console = new StringBuilder();
        Thread outPump = pump(vm.process().getInputStream(), console);
        Thread errPump = pump(vm.process().getErrorStream(), console);

        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest prep = erm.createClassPrepareRequest();
        prep.addClassFilter("Solution");
        prep.enable();
        ExceptionRequest excReq = erm.createExceptionRequest(null, false, true);
        excReq.enable();

        List<Trace.Step> steps = new ArrayList<>();
        Trace.Result result = null;
        String notice = null;
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        MethodEntryRequest entryReq = null;
        MethodExitRequest exitReq = null;
        StepRequest stepReq = null;
        int entryFrames = -1;
        Vals vals = null;

        try {
            EventQueue q = vm.eventQueue();
            boolean done = false;
            while (!done) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    result = new Trace.Result("timeout", null, null,
                            "stopped after 10s — likely an infinite loop or a blocking call", lastLine(steps));
                    notice = "Execution stopped after 10 seconds; showing the " + steps.size() + " steps captured so far.";
                    break;
                }
                EventSet set = q.remove(Math.min(remaining, 250));
                if (set == null) continue;
                for (Event ev : set) {
                    if (ev instanceof VMDeathEvent || ev instanceof VMDisconnectEvent) { done = true; continue; }
                    if (ev instanceof ClassPrepareEvent) {
                        entryReq = erm.createMethodEntryRequest();
                        entryReq.addClassFilter("Solution");
                        entryReq.enable();
                        continue;
                    }
                    if (ev instanceof MethodEntryEvent me && stepReq == null
                            && me.method().name().equals(sig.name())) {
                        ThreadReference t = me.thread();
                        entryFrames = t.frameCount();
                        entryReq.disable();
                        exitReq = erm.createMethodExitRequest();
                        exitReq.addClassFilter("Solution");
                        exitReq.enable();
                        stepReq = erm.createStepRequest(t, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
                        stepReq.addClassFilter("Solution");
                        stepReq.enable();
                        vals = new Vals(t, List.of(stepReq, exitReq, excReq));
                        snapshot(steps, me.location(), t, vals, lineOffset);
                        continue;
                    }
                    if (ev instanceof StepEvent se) {
                        snapshot(steps, se.location(), se.thread(), vals, lineOffset);
                        if (steps.size() >= STEP_CAP) {
                            result = new Trace.Result("stepcap", null, null, "step cap reached", lastLine(steps));
                            notice = "Stopped at the " + STEP_CAP + "-step cap — likely an infinite loop.";
                            done = true;
                        }
                        continue;
                    }
                    if (ev instanceof MethodExitEvent mx && mx.method().name().equals(sig.name())
                            && mx.thread().frameCount() == entryFrames) {
                        Object rv = sig.returnType().equals("void") ? null : vals.serialize(mx.returnValue());
                        result = new Trace.Result("return", rv, sig.returnType(), null, lastLine(steps));
                        if (stepReq != null) stepReq.disable();
                        exitReq.disable();
                        continue;
                    }
                    if (ev instanceof ExceptionEvent xe) {
                        String type = xe.exception().referenceType().name();
                        String msg = exceptionMessage(xe.exception());
                        Integer line = null;
                        try {
                            if (xe.location() != null && xe.location().declaringType().name().equals("Solution"))
                                line = xe.location().lineNumber() - lineOffset;
                        } catch (Exception ignore) {}
                        if (line == null) line = lastLine(steps);
                        result = new Trace.Result("exception", null, type, msg, line);
                        if (stepReq != null) stepReq.disable();
                        continue;
                    }
                }
                if (!done) set.resume();
            }
        } finally {
            try { vm.exit(0); } catch (Exception ignore) {}
            try { vm.process().destroyForcibly(); } catch (Exception ignore) {}
            outPump.join(500);
            errPump.join(500);
        }
        if (result == null) result = new Trace.Result("return", null, sig.returnType(), null, lastLine(steps));
        synchronized (console) { return new Run(steps, result, console.toString(), notice); }
    }

    private static void snapshot(List<Trace.Step> steps, Location loc, ThreadReference t, Vals vals, int off) {
        Map<String, Object> locals = new LinkedHashMap<>();
        try {
            StackFrame f = t.frame(0);
            ObjectReference self = f.thisObject();
            List<LocalVariable> vars = f.visibleVariables();
            Map<LocalVariable, Value> values = f.getValues(vars);
            for (LocalVariable v : vars) locals.put(v.name(), vals.serialize(values.get(v)));
            if (self != null) {
                for (Field fd : self.referenceType().fields()) {
                    if (fd.isStatic() || fd.isSynthetic()) continue;
                    locals.put("this." + fd.name(), vals.serialize(self.getValue(fd)));
                }
            }
        } catch (Exception ignore) { /* AbsentInformation etc: keep the step with whatever we got */ }
        steps.add(new Trace.Step(steps.size(), loc.lineNumber() - off, locals, List.of(), List.of()));
    }

    private static Integer lastLine(List<Trace.Step> steps) {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1).line();
    }

    private static String exceptionMessage(ObjectReference exc) {
        try {
            Field f = exc.referenceType().allFields().stream()
                    .filter(x -> x.name().equals("detailMessage")).findFirst().orElse(null);
            if (f == null) return null;
            Value v = exc.getValue(f);
            return v instanceof StringReference s ? s.value() : null;
        } catch (Exception e) { return null; }
    }

    private static Thread pump(java.io.InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (sink) { sink.append(line).append('\n'); }
                }
            } catch (Exception ignore) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
