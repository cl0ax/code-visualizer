package viz.compile;

import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Normalizes pasted code, compiles Solution + __Driver to a temp dir with -g,
 *  and maps diagnostic lines back to the pasted text. */
public final class Compiler {
    private Compiler() {}

    /** source: what we compile. offset: compiledLine - pastedLine (0 or 1). */
    public record Normalized(String source, int offset) {}

    public record Problem(int line, String message) {}

    /** classDir is null when errors is non-empty. classDir is a fresh temp directory; the caller owns its lifetime (v1 never deletes them during a session). */
    public record Result(Path classDir, List<Problem> errors, Normalized normalized) {}

    public static Normalized normalize(String pasted) {
        String[] lines = pasted.split("\n", -1);
        StringBuilder b = new StringBuilder();
        boolean hasImport = false;
        for (String line : lines) {
            String t = line.strip();
            if (t.startsWith("package ")) { b.append('\n'); continue; }  // blank in place: no line shift
            if (t.startsWith("import ")) hasImport = true;
            b.append(line).append('\n');
        }
        String body = b.toString();
        if (!hasImport) return new Normalized("import java.util.*;\n" + body, 1);
        return new Normalized(body, 0);
    }

    public static Result compile(String pasted, String driverSource) throws IOException {
        Normalized norm = normalize(pasted);
        Path dir = Files.createTempDirectory("viz");
        Path sol = dir.resolve("Solution.java");
        Path drv = dir.resolve("__Driver.java");
        Files.writeString(sol, norm.source());
        Files.writeString(drv, driverSource);

        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = jc.getStandardFileManager(diags, null, null)) {
            var units = fm.getJavaFileObjectsFromPaths(List.of(sol, drv));
            boolean ok = jc.getTask(null, fm, diags,
                    List.of("-g", "-d", dir.toString(), "-proc:none"), null, units).call();
            if (ok) return new Result(dir, List.of(), norm);
            List<Problem> errors = new ArrayList<>();
            for (var d : diags.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                boolean inSolution = d.getSource() != null && d.getSource().getName().endsWith("Solution.java");
                int line = (int) Math.max(1, d.getLineNumber() - (inSolution ? norm.offset() : 0));
                String where = inSolution ? "" : "[generated driver] ";
                errors.add(new Problem(line, where + d.getMessage(null)));
            }
            if (errors.isEmpty()) errors.add(new Problem(1, "compilation failed"));
            return new Result(null, errors, norm);
        }
    }
}
