import viz.util.Json;
import java.util.*;

public class TestJson {
    public record Pt(int x, String label) {}
    public static void main(String[] a) {
        T.eq(Json.write(Map.of("a", 1L)), "{\"a\":1}", "map");
        T.eq(Json.write(List.of(1L, true, "x\n")), "[1,true,\"x\\n\"]", "list + escape");
        T.eq(Json.write(new Pt(3, "hi")), "{\"x\":3,\"label\":\"hi\"}", "record via reflection");
        T.eq(Json.write(null), "null", "null");
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"s\":\"a\\u0041\",\"n\":-2.5,\"i\":7,\"l\":[null,false]}");
        T.eq(m.get("s"), "aA", "unicode escape");
        T.eq(m.get("n"), -2.5, "double");
        T.eq(m.get("i"), 7L, "integer parses as Long");
        T.eq(((List<?>) m.get("l")).get(0), null, "null in array");
        T.eq(((List<?>) m.get("l")).get(1), false, "false in array");
        try { Json.parse("+1"); T.check(false, "+1 should be rejected"); }
        catch (IllegalArgumentException e) { T.check(true, "+1 rejected"); }
        try { Json.parse("\"ab\\u00\""); T.check(false, "truncated unicode should be rejected"); }
        catch (IllegalArgumentException e) { T.check(true, "truncated unicode rejected"); }
        T.done("TestJson");
    }
}
