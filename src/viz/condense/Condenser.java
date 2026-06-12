package viz.condense;

import viz.model.Trace;
import java.util.*;

/** Groups raw steps into semantic steps: init, one per outermost-loop iteration, result.
 *  Captions are templates over real state diffs — never invented. */
public final class Condenser {
    private Condenser() {}

    public static List<Trace.Group> groups(List<Trace.Step> steps, Trace.Result result) {
        List<Trace.Group> out = new ArrayList<>();
        if (steps.isEmpty()) return out;

        // Known v1 limitation: with two sequential top-level loops, the smaller head wins and later loops are absorbed into the final group (wrong-but-graceful).
        int head = Integer.MAX_VALUE;
        for (int i = 1; i < steps.size(); i++)
            if (steps.get(i).line() < steps.get(i - 1).line())
                head = Math.min(head, steps.get(i).line());

        if (head == Integer.MAX_VALUE) {
            out.add(new Trace.Group("run", caption(steps, 0, steps.size() - 1), 0, steps.size() - 1));
        } else {
            int start = 0, iter = 0;
            for (int i = 1; i <= steps.size(); i++) {
                boolean boundary = i == steps.size() || (steps.get(i).line() == head && i > start);
                if (!boundary) continue;
                String label = (start == 0 && steps.get(0).line() != head) ? "init" : "iteration " + (++iter);
                out.add(new Trace.Group(label, caption(steps, start, i - 1), start, i - 1));
                start = i;
            }
        }
        Trace.Group lastG = out.get(out.size() - 1);
        out.set(out.size() - 1,
                new Trace.Group(lastG.label(), lastG.caption() + resultText(result), lastG.from(), lastG.to()));
        return out;
    }

    private static String caption(List<Trace.Step> steps, int from, int to) {
        Map<String, Object> a = steps.get(from).locals(), b = steps.get(to).locals();
        List<String> parts = new ArrayList<>();
        for (var e : b.entrySet()) {
            String before = brief(a.get(e.getKey())), after = brief(e.getValue());
            if (before == null) parts.add(e.getKey() + "=" + after);
            else if (!before.equals(after)) parts.add(e.getKey() + " " + before + "→" + after);
            if (parts.size() >= 4) break;
        }
        return parts.isEmpty() ? "no visible change" : String.join(", ", parts);
    }

    static String brief(Object val) {
        if (val == null) return null;
        Map<?, ?> m = (Map<?, ?>) val;
        Object kind = m.get("kind");
        if ("string".equals(kind)) {
            String s = String.valueOf(m.get("v"));
            return '"' + (s.length() > 12 ? s.substring(0, 12) + "…" : s) + '"';
        }
        if ("array".equals(kind) || "list".equals(kind) || "set".equals(kind) || "map".equals(kind))
            return kind + "[" + m.get("len") + "]";
        if ("null".equals(kind)) return "null";
        if ("char".equals(kind)) return "'" + m.get("v") + "'";
        if ("object".equals(kind)) return String.valueOf(m.get("v"));
        return String.valueOf(m.get("v"));
    }

    static String resultText(Trace.Result r) {
        if (r == null) return "";
        return switch (r.kind()) {
            case "return" -> r.value() == null ? " — done" : " — return " + brief(r.value());
            case "exception" -> " — threw " + r.type() + (r.message() == null ? "" : ": " + r.message());
            case "timeout" -> " — timed out";
            case "stepcap" -> " — step cap reached";
            default -> "";
        };
    }
}
