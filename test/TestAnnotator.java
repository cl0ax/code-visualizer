import viz.annotate.Annotator;
import viz.model.Trace;
import java.util.*;

public class TestAnnotator {
    public static void main(String[] a) {
        List<Trace.Step> steps = List.of(new Trace.Step(0, 3, Map.of(
                "l", Map.<String, Object>of("kind", "int", "v", "0"),
                "r", Map.<String, Object>of("kind", "int", "v", "5"),
                "k", Map.<String, Object>of("kind", "string", "v", "x", "len", 1),
                "s", Map.<String, Object>of("kind", "string", "v", "abcdef", "len", 6),
                "arr", Map.<String, Object>of("kind", "array", "len", 3)), List.of(), List.of()));

        String src = "while (l < r) { char c = s.charAt(l); int v = arr[r]; }";
        Map<String, String> p = Annotator.pointers(src, steps);
        T.eq(p.get("l"), "s", "charAt binding");
        T.eq(p.get("r"), "arr", "array index binding");
        T.eq(p.get("k"), null, "string local is not a pointer");
        T.eq(Annotator.pointers("int q = m.get(key);", steps).get("key"), null,
                "unknown locals never bind");
        T.done("TestAnnotator");
    }
}
