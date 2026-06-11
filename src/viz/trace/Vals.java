package viz.trace;

import com.sun.jdi.*;
import com.sun.jdi.request.EventRequest;
import java.util.*;

/** Serializes JDI values into render-ready maps {kind, ...}. Reads standard collection internals
 *  structurally (honest, fast); falls back to a debuggee toString() for unrecognized types. */
final class Vals {
    static final int MAX_ELEMENTS = 64;
    static final int MAX_STRING = 200;
    static final int MAX_DEPTH = 2;

    private final ThreadReference thread;
    private final List<EventRequest> pauseDuringInvoke;

    Vals(ThreadReference thread, List<EventRequest> pauseDuringInvoke) {
        this.thread = thread;
        this.pauseDuringInvoke = pauseDuringInvoke;
    }

    Map<String, Object> serialize(Value v) { return serialize(v, 0); }

    private Map<String, Object> serialize(Value v, int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (v == null) { m.put("kind", "null"); return m; }
        if (v instanceof PrimitiveValue) {
            if (v instanceof BooleanValue b) { m.put("kind", "boolean"); m.put("v", String.valueOf(b.value())); }
            else if (v instanceof CharValue c) { m.put("kind", "char"); m.put("v", String.valueOf(c.value())); }
            else if (v instanceof DoubleValue d) { m.put("kind", "double"); m.put("v", String.valueOf(d.value())); }
            else if (v instanceof FloatValue f) { m.put("kind", "double"); m.put("v", String.valueOf(f.value())); }
            else if (v instanceof LongValue l) { m.put("kind", "long"); m.put("v", String.valueOf(l.value())); }
            else { m.put("kind", "int"); m.put("v", String.valueOf(((PrimitiveValue) v).intValue())); }
            return m;
        }
        if (v instanceof StringReference s) {
            String str = s.value();
            m.put("kind", "string");
            m.put("len", str.length());
            if (str.length() > MAX_STRING) { m.put("v", str.substring(0, MAX_STRING)); m.put("truncated", true); }
            else m.put("v", str);
            return m;
        }
        if (v instanceof ArrayReference a) {
            if (depth >= MAX_DEPTH) { m.put("kind", "object"); m.put("type", a.referenceType().name()); m.put("v", "(depth limit)"); return m; }
            m.put("kind", "array");
            m.put("len", a.length());
            List<Object> els = new ArrayList<>();
            int n = Math.min(a.length(), MAX_ELEMENTS);
            List<Value> vs = a.length() == 0 ? List.of() : a.getValues(0, n);
            for (Value el : vs) els.add(serialize(el, depth + 1));
            if (a.length() > MAX_ELEMENTS) m.put("truncated", true);
            m.put("elements", els);
            return m;
        }
        ObjectReference o = (ObjectReference) v;
        String cls = o.referenceType().name();
        switch (cls) {
            case "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
                 "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character":
                return serialize(field(o, "value"), depth);
        }
        try {
            if (depth < MAX_DEPTH) {
                if (cls.equals("java.util.ArrayList")) return list(o, depth, m);
                if (cls.equals("java.util.HashMap") || cls.equals("java.util.LinkedHashMap")) return map(o, depth, m);
                if (cls.equals("java.util.HashSet") || cls.equals("java.util.LinkedHashSet")) {
                    ObjectReference backing = (ObjectReference) field(o, "map");
                    Map<String, Object> inner = map(backing, depth, new LinkedHashMap<>());
                    m.put("kind", "set");
                    m.put("len", inner.get("len"));
                    List<Object> els = new ArrayList<>();
                    for (Object e : (List<?>) inner.get("entries")) els.add(((List<?>) e).get(0));
                    m.put("elements", els);
                    if (inner.containsKey("truncated")) m.put("truncated", true);
                    return m;
                }
            }
        } catch (Exception ignore) { /* fall through to toString */ }
        m.put("kind", "object");
        m.put("type", cls);
        m.put("v", safeToString(o));
        return m;
    }

    private Map<String, Object> list(ObjectReference o, int depth, Map<String, Object> m) {
        int size = ((IntegerValue) field(o, "size")).value();
        ArrayReference data = (ArrayReference) field(o, "elementData");
        m.put("kind", "list");
        m.put("len", size);
        List<Object> els = new ArrayList<>();
        int n = Math.min(size, MAX_ELEMENTS);
        List<Value> vs = n == 0 ? List.of() : data.getValues(0, n);
        for (Value el : vs) els.add(serialize(el, depth + 1));
        if (size > MAX_ELEMENTS) m.put("truncated", true);
        m.put("elements", els);
        return m;
    }

    private Map<String, Object> map(ObjectReference o, int depth, Map<String, Object> m) {
        m.put("kind", "map");
        m.put("len", ((IntegerValue) field(o, "size")).value());
        List<Object> entries = new ArrayList<>();
        boolean truncated = false;
        boolean linked = o.referenceType().name().equals("java.util.LinkedHashMap");
        if (linked) {
            ObjectReference node = (ObjectReference) field(o, "head");
            while (node != null) {
                if (entries.size() >= MAX_ELEMENTS) { truncated = true; break; }
                entries.add(List.of(serialize(field(node, "key"), depth + 1), serialize(field(node, "value"), depth + 1)));
                node = (ObjectReference) field(node, "after");
            }
        } else {
            Value tableV = field(o, "table");
            if (tableV instanceof ArrayReference table) {
                List<Value> buckets = table.length() == 0 ? List.of() : table.getValues(0, table.length());
                outer:
                for (Value bv : buckets) {
                    ObjectReference node = (ObjectReference) bv;
                    while (node != null) {
                        if (entries.size() >= MAX_ELEMENTS) { truncated = true; break outer; }
                        entries.add(List.of(serialize(field(node, "key"), depth + 1), serialize(field(node, "value"), depth + 1)));
                        node = (ObjectReference) field(node, "next");
                    }
                }
            }
        }
        m.put("entries", entries);
        if (truncated) m.put("truncated", true);
        return m;
    }

    /** Field lookup including private inherited fields (fieldByName skips those). */
    private static Value field(ObjectReference o, String name) {
        Field f = o.referenceType().allFields().stream()
                .filter(x -> x.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no field " + name + " on " + o.referenceType().name()));
        return o.getValue(f);
    }

    private String safeToString(ObjectReference o) {
        try {
            for (EventRequest r : pauseDuringInvoke) r.disable();
            Method ts = o.referenceType().methodsByName("toString", "()Ljava/lang/String;").get(0);
            Value r = o.invokeMethod(thread, ts, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
            String s = r instanceof StringReference sr ? sr.value() : String.valueOf(r);
            return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) + "…" : s;
        } catch (Exception e) {
            return "(" + o.referenceType().name() + ")";
        } finally {
            for (EventRequest r : pauseDuringInvoke) r.enable();
        }
    }
}
