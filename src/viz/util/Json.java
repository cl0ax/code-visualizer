package viz.util;

import java.lang.reflect.RecordComponent;
import java.util.*;

/** Minimal zero-dependency JSON. Parses to Map/List/String/Long/Double/Boolean/null.
 *  Writes those plus any java record (via record components, in declaration order). */
public final class Json {
    private Json() {}

    // ---------- writing ----------
    public static String write(Object o) { StringBuilder b = new StringBuilder(); writeVal(o, b); return b.toString(); }

    private static void writeVal(Object o, StringBuilder b) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof String s) { writeString(s, b); return; }
        if (o instanceof Double d) { b.append(d.isInfinite() || d.isNaN() ? "null" : d.toString()); return; }
        if (o instanceof Boolean || o instanceof Number) { b.append(o); return; }
        if (o instanceof Map<?, ?> m) {
            b.append('{'); boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) b.append(','); first = false;
                writeString(String.valueOf(e.getKey()), b); b.append(':'); writeVal(e.getValue(), b);
            }
            b.append('}'); return;
        }
        if (o instanceof Collection<?> c) {
            b.append('['); boolean first = true;
            for (Object e : c) { if (!first) b.append(','); first = false; writeVal(e, b); }
            b.append(']'); return;
        }
        if (o instanceof Record r) {
            b.append('{'); boolean first = true;
            for (RecordComponent rc : r.getClass().getRecordComponents()) {
                Object v;
                try { rc.getAccessor().setAccessible(true); v = rc.getAccessor().invoke(r); }
                catch (Exception ex) { throw new RuntimeException(ex); }
                if (!first) b.append(','); first = false;
                writeString(rc.getName(), b); b.append(':'); writeVal(v, b);
            }
            b.append('}'); return;
        }
        writeString(String.valueOf(o), b);
    }

    private static void writeString(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        b.append('"');
    }

    // ---------- parsing ----------
    public static Object parse(String s) {
        P p = new P(s);
        Object v = p.value();
        p.ws();
        if (p.i < p.n) throw p.err("trailing characters");
        return v;
    }

    private static final class P {
        final String s; int i = 0; final int n;
        P(String s) { this.s = s; this.n = s.length(); }
        RuntimeException err(String msg) { return new IllegalArgumentException("JSON error at " + i + ": " + msg); }
        void ws() { while (i < n && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { if (i >= n) throw err("unexpected end"); return s.charAt(i); }
        void expect(char c) { if (i >= n || s.charAt(i) != c) throw err("expected '" + c + "'"); i++; }

        Object value() {
            ws();
            char c = peek();
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (c == 't') { lit("true"); return Boolean.TRUE; }
            if (c == 'f') { lit("false"); return Boolean.FALSE; }
            if (c == 'n') { lit("null"); return null; }
            return num();
        }
        void lit(String w) { if (!s.startsWith(w, i)) throw err("invalid literal"); i += w.length(); }
        Map<String, Object> obj() {
            expect('{'); ws();
            Map<String, Object> m = new LinkedHashMap<>();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws(); String k = str(); ws(); expect(':');
                m.put(k, value()); ws();
                char c = peek(); i++;
                if (c == '}') return m;
                if (c != ',') throw err("expected ',' or '}'");
            }
        }
        List<Object> arr() {
            expect('['); ws();
            List<Object> l = new ArrayList<>();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value()); ws();
                char c = peek(); i++;
                if (c == ']') return l;
                if (c != ',') throw err("expected ',' or ']'");
            }
        }
        String str() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek(); i++;
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = peek(); i++;
                    switch (e) {
                        case '"' -> b.append('"'); case '\\' -> b.append('\\'); case '/' -> b.append('/');
                        case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> throw err("bad escape");
                    }
                } else b.append(c);
            }
        }
        Object num() {
            int start = i;
            if (peek() == '-') i++;
            boolean dbl = false;
            while (i < n) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') i++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { dbl = true; i++; }
                else break;
            }
            String t = s.substring(start, i);
            if (t.isEmpty() || t.equals("-")) throw err("bad number");
            return dbl ? (Object) Double.parseDouble(t) : (Object) Long.parseLong(t);
        }
    }
}
