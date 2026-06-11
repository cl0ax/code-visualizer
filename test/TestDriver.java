import viz.compile.Compiler;
import viz.driver.*;
import viz.model.Sig;
import java.util.List;

public class TestDriver {
    public static void main(String[] a) throws Exception {
        String code = "class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0}; } }";
        Sig sig = Analyzer.publicMethods(code).get(0);
        String drv = Driver.generate(sig, List.of(
                Inputs.toJava(0, "int[]", "[2,7]"), Inputs.toJava(1, "int", "9")));
        T.check(drv.contains("public class __Driver"), "class name");
        T.check(drv.contains("Solution __sol = new Solution();"), "instance created");
        T.check(drv.contains("var __r = __sol.twoSum(p0, p1);"), "call with refs");
        T.check(Compiler.compile(code, drv).classDir() != null, "driver + solution compile together");

        String code2 = "class Solution { public static void go(int x) { } }";
        Sig sig2 = Analyzer.publicMethods(code2).get(0);
        String drv2 = Driver.generate(sig2, List.of(Inputs.toJava(0, "int", "1")));
        T.check(drv2.contains("Solution.go(p0);"), "static call, no instance");
        T.check(!drv2.contains("__r"), "void: no result capture");
        T.check(Compiler.compile(code2, drv2).classDir() != null, "static void driver compiles");
        T.done("TestDriver");
    }
}
