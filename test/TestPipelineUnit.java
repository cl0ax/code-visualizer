import viz.Pipeline;
import viz.util.Json;
import java.util.*;

public class TestPipelineUnit {
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
        Map<String, Object> an = Pipeline.analyze(code);
        T.eq(an.get("ok"), true, "analyze ok");
        T.eq(an.get("methods") instanceof List<?> l ? l.size() : 0, 1, "one method");

        Map<String, Object> bad = Pipeline.analyze("class Solution { }");
        T.eq(bad.get("ok"), false, "no method -> not ok");

        // Round-trip through JSON: tests exactly what the frontend will see.
        Map<String, Object> out = (Map<String, Object>) Json.parse(Json.write(
                Pipeline.trace(code, List.of("[3,4,5]"), null)));
        T.eq(out.get("ok"), true, "trace ok, got " + Json.write(out));
        Map<String, Object> trace = (Map<String, Object>) out.get("trace");
        T.eq(trace.get("source"), code, "source passed through verbatim");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) (List<?>) trace.get("steps");
        T.check(steps.size() >= 8, "steps present");
        T.check(((List<?>) steps.get(0).get("changed")).size() > 0, "first step changed[] non-empty");
        boolean iChanged = false;
        for (var s : steps) if (((List<?>) s.get("changed")).contains("i")) iChanged = true;
        T.check(iChanged, "changed[] tracks the loop counter");
        T.check(((Map<String, Object>) trace.get("pointers")).get("i") != null, "i bound as pointer");
        T.check(((List<?>) trace.get("groups")).size() >= 3, "semantic groups present");

        Map<String, Object> ce = Pipeline.trace("class Solution { public int f(int x) { return y; } }",
                List.of("1"), null);
        T.eq(ce.get("ok"), false, "compile error not ok");
        T.eq(ce.get("stage"), "compile", "compile stage");

        Map<String, Object> ie = Pipeline.trace(code, List.of("oops"), null);
        T.eq(ie.get("stage"), "input", "input stage");
        T.eq(ie.get("param"), 0, "param index");
        T.done("TestPipelineUnit");
    }
}
