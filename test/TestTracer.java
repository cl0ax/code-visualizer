import viz.compile.Compiler;
import viz.driver.*;
import viz.model.Sig;
import viz.trace.Tracer;
import java.util.List;
import java.util.Map;

public class TestTracer {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        String code = """
            class Solution {
                public int sum(int[] nums) {
                    int total = 0;
                    for (int i = 0; i < nums.length; i++) {
                        total += nums[i];
                    }
                    return total;
                }
            }
            """;
        Sig sig = Analyzer.publicMethods(code).get(0);
        var cr = Compiler.compile(code, Driver.generate(sig, List.of(Inputs.toJava(0, "int[]", "[3,4,5]"))));
        T.check(cr.classDir() != null, "fixture compiles");
        Tracer.Run run = Tracer.run(cr.classDir(), sig, cr.normalized().offset());

        T.check(run.steps().size() >= 8, "collected steps, got " + run.steps().size());
        for (var s : run.steps())
            T.check(s.line() >= 2 && s.line() <= 8, "line in pasted range, got " + s.line());

        Map<String, Object> first = run.steps().get(0).locals();
        T.check(first.containsKey("nums"), "param visible at entry");
        Map<String, Object> nums = (Map<String, Object>) first.get("nums");
        T.eq(nums.get("kind"), "array", "array kind");
        T.eq(((List<Object>) nums.get("elements")).size(), 3, "array elements");
        Map<String, Object> el0 = (Map<String, Object>) ((List<Object>) nums.get("elements")).get(0);
        T.eq(el0.get("v"), "3", "element value");

        Map<String, Object> lastLocals = run.steps().get(run.steps().size() - 1).locals();
        T.eq(((Map<String, Object>) lastLocals.get("total")).get("v"), "12", "total = 12 at the end");

        T.eq(run.result().kind(), "return", "returned");
        T.eq(((Map<String, Object>) run.result().value()).get("v"), "12", "return value 12");
        T.check(run.notice() == null, "no notice on a clean run");
        String exCode = """
            class Solution {
                public int boom(int[] nums) {
                    int i = nums.length;
                    return nums[i];
                }
            }
            """;
        Sig exSig = Analyzer.publicMethods(exCode).get(0);
        var exCr = Compiler.compile(exCode, Driver.generate(exSig, List.of(Inputs.toJava(0, "int[]", "[1,2]"))));
        Tracer.Run exRun = Tracer.run(exCr.classDir(), exSig, exCr.normalized().offset());
        T.eq(exRun.result().kind(), "exception", "exception kind");
        T.check(exRun.result().type().contains("ArrayIndexOutOfBounds"), "exception type");
        T.eq(exRun.result().line(), Integer.valueOf(4), "exception line mapped");

        String vCode = """
            class Solution {
                public void noop(int x) {
                    int y = x + 1;
                }
            }
            """;
        Sig vSig = Analyzer.publicMethods(vCode).get(0);
        var vCr = Compiler.compile(vCode, Driver.generate(vSig, List.of(Inputs.toJava(0, "int", "1"))));
        Tracer.Run vRun = Tracer.run(vCr.classDir(), vSig, vCr.normalized().offset());
        T.eq(vRun.result().kind(), "return", "void returns");
        T.check(vRun.result().value() == null, "void result value null");
        T.eq(vRun.result().type(), "void", "void type");

        T.done("TestTracer");
    }
}
