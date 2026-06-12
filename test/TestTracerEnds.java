import viz.compile.Compiler;
import viz.driver.*;
import viz.model.Sig;
import viz.trace.Tracer;
import java.nio.file.*;
import java.util.List;

public class TestTracerEnds {
    public static void main(String[] a) throws Exception {
        // 1) Uncaught exception → kind=exception with type, message, pasted line
        Tracer.Run oob = run("bench/oob/Solution.java", "[1,2]");
        T.eq(oob.result().kind(), "exception", "exception kind");
        T.check(oob.result().type().contains("ArrayIndexOutOfBounds"), "exception type, got " + oob.result().type());
        T.check(oob.result().message() != null, "exception message present");
        T.eq(oob.result().line(), Integer.valueOf(4), "throw line mapped to pasted source");
        T.check(oob.steps().size() >= 2, "steps before the throw");

        // 2) Blocking call → timeout (~10s wall clock, expected)
        Tracer.Run hang = run("bench/hang/Solution.java", "1");
        T.eq(hang.result().kind(), "timeout", "timeout kind");
        T.check(hang.notice() != null, "timeout notice present");

        // 3) Tight infinite loop → step cap (or timeout on a very slow machine; both honest stops)
        Tracer.Run spin = run("bench/spin/Solution.java", "0");
        T.check(spin.result().kind().equals("stepcap") || spin.result().kind().equals("timeout"),
                "spin stopped, got " + spin.result().kind());
        T.check(spin.notice() != null, "spin notice present");
        T.done("TestTracerEnds");
    }

    static Tracer.Run run(String file, String input) throws Exception {
        String code = Files.readString(Path.of(file));
        Sig sig = Analyzer.publicMethods(code).get(0);
        var cr = Compiler.compile(code, Driver.generate(sig,
                List.of(Inputs.toJava(0, sig.params().get(0).type(), input))));
        T.check(cr.classDir() != null, file + " compiles");
        return Tracer.run(cr.classDir(), sig, cr.normalized().offset());
    }
}
