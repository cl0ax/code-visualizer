import viz.util.Json;
import java.nio.file.*;
import java.util.*;

public class TestPipeline {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        // 0) Analyze wire-format coverage — round-trip through JSON serialization
        String pcode = Files.readString(Path.of("bench/valid-palindrome/Solution.java"));
        @SuppressWarnings("unchecked")
        Map<String, Object> an = (Map<String, Object>) Json.parse(Json.write(viz.Pipeline.analyze(pcode)));
        T.eq(an.get("ok"), true, "analyze ok over wire");
        List<Map<String, Object>> ms = (List<Map<String, Object>>) (List<?>) an.get("methods");
        T.eq(ms.get(0).get("name"), "isPalindrome", "analyze method name");
        T.eq(((List<?>) ms.get(0).get("params")).size(), 1, "analyze param count");

        // 1) Valid Palindrome (accepted) — classic input → true; pointers bound to charStr
        Map<String, Object> t1 = run("bench/valid-palindrome/Solution.java",
                List.of("\"A man, a plan, a canal: Panama\""));
        eqReturn(t1, "true", "palindrome true");
        Map<String, Object> ptrs = (Map<String, Object>) t1.get("pointers");
        T.eq(ptrs.get("left"), "charStr", "left bound to charStr");
        T.eq(ptrs.get("right"), "charStr", "right bound to charStr");
        T.check(((List<?>) t1.get("groups")).size() >= 3, "semantic groups");

        // 2) The real first-submit WA code → reproduces the wrong answer (false) on the failing input
        Map<String, Object> t2 = run("bench/valid-palindrome-wa/Solution.java",
                List.of("\"Was it a car or a cat I saw?\""));
        eqReturn(t2, "false", "WA reproduced — the debugging use case");

        // 3) Two Sum brute force → [0, 1]
        Map<String, Object> t3 = run("bench/two-sum/Solution.java", List.of("[2,7,11,15]", "9"));
        Map<String, Object> v3 = (Map<String, Object>) ((Map<String, Object>) t3.get("result")).get("value");
        T.eq(v3.get("kind"), "array", "returns array");
        List<Map<String, Object>> els = (List<Map<String, Object>>) (List<?>) v3.get("elements");
        T.eq(els.get(0).get("v"), "0", "result[0]");
        T.eq(els.get(1).get("v"), "1", "result[1]");

        // 4) Contains Duplicate → true (HashSet/set renderer path)
        eqReturn(run("bench/contains-duplicate/Solution.java", List.of("[1,2,3,1]")), "true", "duplicate");

        // 5) Group Anagrams → list of 3 groups (HashMap/map renderer path, rich nested return)
        Map<String, Object> t5 = run("bench/group-anagrams/Solution.java",
                List.of("[\"eat\",\"tea\",\"tan\",\"ate\",\"nat\",\"bat\"]"));
        Map<String, Object> v5 = (Map<String, Object>) ((Map<String, Object>) t5.get("result")).get("value");
        T.eq(v5.get("kind"), "list", "anagrams returns list");
        T.eq(String.valueOf(v5.get("len")), "3", "3 anagram groups");

        // 6) oob fixture through the full pipeline → exception surfaces in result and last caption
        Map<String, Object> t6 = run("bench/oob/Solution.java", List.of("[1,2]"));
        Map<String, Object> r6 = (Map<String, Object>) t6.get("result");
        T.eq(r6.get("kind"), "exception", "exception kind");
        List<Map<String, Object>> g6 = (List<Map<String, Object>>) (List<?>) t6.get("groups");
        T.check(String.valueOf(g6.get(g6.size() - 1).get("caption")).contains("threw"),
                "exception in last caption");

        T.done("TestPipeline");
    }

    /** Runs the pipeline and round-trips through JSON — asserting the exact wire format. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> run(String file, List<String> inputs) throws Exception {
        String code = Files.readString(Path.of(file));
        Map<String, Object> out = (Map<String, Object>) Json.parse(Json.write(
                viz.Pipeline.trace(code, inputs, null)));
        T.check(Boolean.TRUE.equals(out.get("ok")), file + " ok, got " + Json.write(out));
        Map<String, Object> trace = (Map<String, Object>) out.get("trace");
        List<?> steps = (List<?>) trace.get("steps");
        T.check(!steps.isEmpty(), file + " has steps");
        long sourceLines = String.valueOf(trace.get("source")).lines().count();
        for (Object so : steps) {
            long line = (Long) ((Map<String, Object>) so).get("line");
            T.check(line >= 1 && line <= sourceLines, file + " line in source bounds: " + line);
        }
        return trace;
    }

    @SuppressWarnings("unchecked")
    static void eqReturn(Map<String, Object> trace, String v, String msg) {
        Map<String, Object> res = (Map<String, Object>) trace.get("result");
        T.eq(res.get("kind"), "return", msg + " (kind)");
        T.eq(((Map<String, Object>) res.get("value")).get("v"), v, msg);
    }
}
