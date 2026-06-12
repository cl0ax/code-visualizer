package viz.annotate;

import viz.driver.Analyzer;
import viz.model.Trace;
import java.util.*;
import java.util.regex.*;

/** Binds int locals to the array/String/list locals they index, from source usage.
 *  Emitted as trace metadata {pointer -> target}; the renderer draws bound ints as
 *  markers under the target's cells (red-pinned at the edge when out of range). */
public final class Annotator {
    private Annotator() {}

    private static final List<Pattern> USES = List.of(
            Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\[\\s*([A-Za-z_$][\\w$]*)\\s*\\]"),
            Pattern.compile("([A-Za-z_$][\\w$]*)\\.charAt\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)"),
            Pattern.compile("([A-Za-z_$][\\w$]*)\\.substring\\(\\s*([A-Za-z_$][\\w$]*)"),
            Pattern.compile("([A-Za-z_$][\\w$]*)\\.get\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)"));

    public static Map<String, String> pointers(String source, List<Trace.Step> steps) {
        Set<String> ints = new HashSet<>(), targets = new HashSet<>();
        source = Analyzer.stripCommentsAndStrings(source);
        for (Trace.Step s : steps) {
            for (var e : s.locals().entrySet()) {
                Object kind = ((Map<?, ?>) e.getValue()).get("kind");
                if ("int".equals(kind) || "long".equals(kind)) ints.add(e.getKey());
                if ("array".equals(kind) || "string".equals(kind) || "list".equals(kind)) targets.add(e.getKey());
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Pattern p : USES) {
            Matcher m = p.matcher(source);
            while (m.find()) {
                String target = m.group(1), idx = m.group(2);
                if (ints.contains(idx) && targets.contains(target) && !out.containsKey(idx))
                    out.put(idx, target);
            }
        }
        return out;
    }
}
