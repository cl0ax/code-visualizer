package viz.driver;

import viz.model.Sig;
import java.util.List;
import java.util.stream.Collectors;

/** Generates the __Driver class that calls the entry method with baked-in inputs.
 *  Driver lines are never traced (the step request is class-filtered to Solution). */
public final class Driver {
    private Driver() {}

    public static String generate(Sig sig, List<Inputs.JavaArg> args) {
        String params = args.stream().map(Inputs.JavaArg::ref).collect(Collectors.joining(", "));
        String call = (sig.isStatic() ? "Solution." : "__sol.") + sig.name() + "(" + params + ")";
        StringBuilder b = new StringBuilder();
        b.append("public class __Driver {\n");
        b.append("    public static void main(String[] __args) throws Exception {\n");
        if (!sig.isStatic()) b.append("        Solution __sol = new Solution();\n");
        for (Inputs.JavaArg a : args) b.append(a.decl());
        if (sig.returnType().equals("void")) b.append("        ").append(call).append(";\n");
        else b.append("        var __r = ").append(call).append(";\n");
        b.append("    }\n}\n");
        return b.toString();
    }
}
