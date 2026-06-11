package viz.model;

import java.util.List;
import java.util.Map;

/** The trace JSON contract (see Design.md). The frontend consumes this and nothing else.
 *  Local values ("Val"s) are nested Maps shaped {kind, v?, len?, elements?, entries?, type?, truncated?}. */
public record Trace(
        String source,
        Sig entry,
        List<String> inputs,
        Result result,
        Map<String, String> pointers,
        List<Step> steps,
        List<Group> groups,
        String console,
        String notice) {

    /** kind: return | exception | timeout | stepcap */
    public record Result(String kind, Object value, String type, String message, Integer line) {}

    /** One raw line-step. locals: name -> Val map. */
    public record Step(int i, int line, Map<String, Object> locals, List<String> changed, List<String> stdout) {}

    /** A semantic group over raw steps [from..to] inclusive. */
    public record Group(String label, String caption, int from, int to) {}
}
