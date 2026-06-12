import viz.condense.Condenser;
import viz.model.Trace;
import java.util.*;

public class TestCondenser {
    static Trace.Step st(int i, int line, String x) {
        return new Trace.Step(i, line, Map.of("x", Map.<String, Object>of("kind", "int", "v", x)),
                List.of(), List.of());
    }
    public static void main(String[] a) {
        // lines: 2 (init), 3,4,5 | 3,4,5 (two iterations), 6 (return inside iter 2's range)
        List<Trace.Step> steps = List.of(
                st(0, 2, "0"), st(1, 3, "0"), st(2, 4, "1"), st(3, 5, "1"),
                st(4, 3, "1"), st(5, 4, "2"), st(6, 5, "2"), st(7, 6, "2"));
        var groups = Condenser.groups(steps,
                new Trace.Result("return", Map.<String, Object>of("kind", "int", "v", "2"), "int", null, 6));
        T.eq(groups.size(), 3, "init + 2 iterations");
        T.eq(groups.get(0).label(), "init", "init label");
        T.eq(groups.get(1).label(), "iteration 1", "iteration 1");
        T.eq(groups.get(2).label(), "iteration 2", "iteration 2");
        T.eq(groups.get(1).from(), 1, "iter1 from");
        T.eq(groups.get(1).to(), 3, "iter1 to");
        T.check(groups.get(2).caption().contains("return"), "result appended to last caption");
        T.check(groups.get(1).caption().contains("x"), "diff caption mentions changed local");

        var flat = Condenser.groups(List.of(st(0, 2, "0"), st(1, 3, "1")),
                new Trace.Result("return", null, "void", null, 3));
        T.eq(flat.size(), 1, "no loop -> one group");
        T.eq(flat.get(0).label(), "run", "run label");
        T.done("TestCondenser");
    }
}
