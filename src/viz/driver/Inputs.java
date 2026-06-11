package viz.driver;

import viz.util.Json;
import java.util.*;

/** Parses JSON-ish input literals and bakes them into Java statements (decl) with a variable name (ref). */
public final class Inputs {
    private Inputs() {}

    public static final class InputError extends RuntimeException {
        public final int param;
        public InputError(int param, String message) { super(message); this.param = param; }
    }

    /** decl: full Java statements declaring variable `ref`. */
    public record JavaArg(String decl, String ref) {}

    public static JavaArg toJava(int idx, String type, String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.isEmpty()) throw new InputError(idx, "input is empty");
        Object v;
        try {
            if (t.matches("'(\\\\.|[^'\\\\])'")) v = t.substring(1, t.length() - 1);  // char literal
            else v = Json.parse(t);
        } catch (Exception e) {
            throw new InputError(idx, "could not parse literal: " + e.getMessage());
        }
        String ref = "p" + idx;
        Ty ty = Ty.parse(type);
        StringBuilder b = new StringBuilder();
        if (ty.dims == 0 && ty.base.equals("Map")) {
            if (!(v instanceof Map<?, ?> mv)) throw new InputError(idx, "expected an object like {\"a\":1}");
            b.append("        ").append(qualify(type)).append(' ').append(ref).append(" = new java.util.LinkedHashMap<>();\n");
            for (var e : mv.entrySet()) {
                Object key = keyValue(idx, ty.args.get(0), String.valueOf(e.getKey()));
                b.append("        ").append(ref).append(".put(")
                 .append(expr(idx, ty.args.get(0), key)).append(", ")
                 .append(expr(idx, ty.args.get(1), e.getValue())).append(");\n");
            }
        } else {
            b.append("        ").append(qualify(type)).append(' ').append(ref)
             .append(" = ").append(expr(idx, type, v)).append(";\n");
        }
        return new JavaArg(b.toString(), ref);
    }

    /** JSON object keys are strings; coerce to the declared key type. */
    private static Object keyValue(int idx, String keyType, String key) {
        return switch (Ty.parse(keyType).base) {
            case "Integer", "int" -> {
                try { yield Long.parseLong(key); }
                catch (NumberFormatException e) { throw new InputError(idx, "map key '" + key + "' is not an integer"); }
            }
            case "Character", "char" -> key;
            default -> key;
        };
    }

    static String expr(int idx, String type, Object v) {
        Ty ty = Ty.parse(type);
        if (ty.dims > 0) {
            if (v == null) return "null";
            if (!(v instanceof List<?> l)) throw new InputError(idx, "expected an array like [1,2] for " + type);
            String elemType = type.substring(0, type.lastIndexOf('[')).strip();
            StringBuilder b = new StringBuilder("new ").append(qualify(type)).append('{');
            for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(", "); b.append(expr(idx, elemType, l.get(i))); }
            return b.append('}').toString();
        }
        switch (ty.base) {
            case "int", "Integer": {
                if (v instanceof Long n) {
                    if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
                        throw new InputError(idx, "value " + n + " overflows int");
                    return String.valueOf(n);
                }
                throw new InputError(idx, "expected an integer for " + type);
            }
            case "short", "Short", "byte", "Byte":
                if (v instanceof Long n) return String.valueOf(n);
                throw new InputError(idx, "expected an integer for " + type);
            case "long", "Long":
                if (v instanceof Long n) return n + "L";
                throw new InputError(idx, "expected an integer for " + type);
            case "float", "Float": {
                if (v instanceof Long n) return n + ".0f";
                if (v instanceof Double d) return d + "f";
                throw new InputError(idx, "expected a number for " + type);
            }
            case "double", "Double": {
                if (v instanceof Long n) return n + ".0";
                if (v instanceof Double d) return d.toString();
                throw new InputError(idx, "expected a number for " + type);
            }
            case "boolean", "Boolean":
                if (v instanceof Boolean bo) return bo.toString();
                throw new InputError(idx, "expected true or false");
            case "char", "Character": {
                if (v instanceof String s && s.length() == 1) return "'" + escChar(s.charAt(0)) + "'";
                throw new InputError(idx, "expected a single character like 'x'");
            }
            case "String":
                if (v == null) return "null";
                if (v instanceof String s) return '"' + escStr(s) + '"';
                throw new InputError(idx, "expected a string like \"abc\"");
            case "List", "ArrayList": {
                if (v == null) return "null";
                if (!(v instanceof List<?> l)) throw new InputError(idx, "expected a list like [1,2]");
                String e = qualify(ty.args.get(0));
                if (l.isEmpty()) return "new java.util.ArrayList<" + e + ">()";
                return "new java.util.ArrayList<>(java.util.Arrays.<" + e + ">asList(" + joinExprs(idx, ty.args.get(0), l) + "))";
            }
            case "Set", "HashSet", "LinkedHashSet": {
                if (v == null) return "null";
                if (!(v instanceof List<?> l)) throw new InputError(idx, "expected a list like [1,2]");
                String e = qualify(ty.args.get(0));
                if (l.isEmpty()) return "new java.util.LinkedHashSet<" + e + ">()";
                return "new java.util.LinkedHashSet<>(java.util.Arrays.<" + e + ">asList(" + joinExprs(idx, ty.args.get(0), l) + "))";
            }
            case "Map", "HashMap", "LinkedHashMap":
                throw new InputError(idx, "nested maps are not supported in v1");
            default:
                throw new InputError(idx, "unsupported parameter type: " + type);
        }
    }

    private static String joinExprs(int idx, String elemType, List<?> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(", "); b.append(expr(idx, elemType, l.get(i))); }
        return b.toString();
    }

    /** Qualify bare java.util collection names so generated code never needs imports. */
    static String qualify(String type) {
        return type.replaceAll("(?<![.\\w])(List|Map|Set|ArrayList|HashMap|HashSet|LinkedHashMap|LinkedHashSet|Deque|Queue)\\b", "java.util.$1");
    }

    static String escStr(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(escChar(c));
        return b.toString();
    }

    static String escChar(char c) {
        return switch (c) {
            case '\\' -> "\\\\"; case '"' -> "\\\""; case '\'' -> "\\'";
            case '\n' -> "\\n"; case '\t' -> "\\t"; case '\r' -> "\\r";
            default -> String.valueOf(c);
        };
    }

    /** Tiny type descriptor: base name, generic args, array dims. */
    static final class Ty {
        final String base; final List<String> args; final int dims;
        private Ty(String base, List<String> args, int dims) { this.base = base; this.args = args; this.dims = dims; }
        static Ty parse(String type) {
            String t = type.strip();
            int dims = 0;
            while (t.endsWith("[]")) { dims++; t = t.substring(0, t.length() - 2).strip(); }
            int lt = t.indexOf('<');
            if (lt < 0) return new Ty(t, List.of(), dims);
            String base = t.substring(0, lt).strip();
            String inner = t.substring(lt + 1, t.lastIndexOf('>'));
            List<String> args = new ArrayList<>();
            for (String piece : Analyzer.splitTop(inner)) args.add(piece.strip());
            return new Ty(base, args, dims);
        }
    }
}
