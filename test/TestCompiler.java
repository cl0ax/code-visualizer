import viz.compile.Compiler;

public class TestCompiler {
    public static void main(String[] a) throws Exception {
        var norm = Compiler.normalize("package x.y;\nclass Solution { }\n");
        T.check(norm.source().startsWith("import java.util.*;"), "import prepended");
        T.eq(norm.offset(), 1, "offset 1 when import added");
        T.check(norm.source().contains("\n\nclass Solution"), "package blanked in place (no line shift)");

        var norm2 = Compiler.normalize("import java.util.*;\nclass Solution { }\n");
        T.eq(norm2.offset(), 0, "offset 0 when imports already present");

        String driver = "public class __Driver { public static void main(String[] a) { int p0 = 1; var __r = new Solution().id(p0); } }";

        var ok = Compiler.compile("class Solution { public int id(int x) { return x; } }", driver);
        T.check(ok.classDir() != null, "good code compiles");
        T.check(java.nio.file.Files.exists(ok.classDir().resolve("Solution.class")), "Solution.class written");
        T.check(java.nio.file.Files.exists(ok.classDir().resolve("__Driver.class")), "__Driver.class written");

        var fail = Compiler.compile("class Solution { public int id(int x) { return y; } }", driver);
        T.check(fail.classDir() == null, "bad code fails");
        T.eq(fail.errors().get(0).line(), 1, "error line mapped back to pasted line");
        T.check(fail.errors().get(0).message().contains("y"), "error mentions the bad symbol");
        T.done("TestCompiler");
    }
}
