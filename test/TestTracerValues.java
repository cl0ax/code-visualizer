import viz.compile.Compiler;
import viz.driver.*;
import viz.model.Sig;
import viz.trace.Tracer;
import java.util.List;
import java.util.Map;

public class TestTracerValues {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        String code = """
            class Solution {
                public int build(int n) {
                    List<Integer> list = new ArrayList<>();
                    Map<String, Integer> map = new HashMap<>();
                    Set<Integer> set = new HashSet<>();
                    StringBuilder sb = new StringBuilder("xy");
                    for (int i = 0; i < n; i++) {
                        list.add(i);
                        map.put("k" + i, i);
                        set.add(i * 10);
                    }
                    System.out.println("building");
                    return list.size() + map.size() + set.size();
                }
            }
            """;
        Sig sig = Analyzer.publicMethods(code).get(0);
        var cr = Compiler.compile(code, Driver.generate(sig, List.of(Inputs.toJava(0, "int", "3"))));
        Tracer.Run run = Tracer.run(cr.classDir(), sig, cr.normalized().offset());

        Map<String, Object> last = run.steps().get(run.steps().size() - 1).locals();

        Map<String, Object> list = (Map<String, Object>) last.get("list");
        T.eq(list.get("kind"), "list", "list kind");
        T.eq(list.get("len"), 3, "list len");
        T.eq(((Map<String, Object>) ((List<Object>) list.get("elements")).get(1)).get("v"), "1", "Integer unwrapped");

        Map<String, Object> map = (Map<String, Object>) last.get("map");
        T.eq(map.get("kind"), "map", "map kind");
        T.eq(map.get("len"), 3, "map len");
        List<Object> entries = (List<Object>) map.get("entries");
        boolean foundK0 = false;
        for (Object e : entries) {
            List<Object> kv = (List<Object>) e;
            if ("k0".equals(((Map<String, Object>) kv.get(0)).get("v")))
                foundK0 = "0".equals(((Map<String, Object>) kv.get(1)).get("v"));
        }
        T.check(foundK0, "map entry k0 -> 0 present");

        Map<String, Object> set = (Map<String, Object>) last.get("set");
        T.eq(set.get("kind"), "set", "set kind");
        T.eq(((List<Object>) set.get("elements")).size(), 3, "set elements");

        Map<String, Object> sb = (Map<String, Object>) last.get("sb");
        T.eq(sb.get("kind"), "object", "StringBuilder falls back to object");
        T.check(String.valueOf(sb.get("v")).contains("xy"), "toString fallback content, got " + sb.get("v"));

        T.check(run.console().contains("building"), "stdout captured in console");
        T.eq(((Map<String, Object>) run.result().value()).get("v"), "9", "return 9");
        T.done("TestTracerValues");
    }
}
