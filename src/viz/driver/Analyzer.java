package viz.driver;

import viz.model.Sig;
import java.util.*;
import java.util.regex.*;

/** Finds public methods of the pasted Solution class with a lightweight scan (no full parser). */
public final class Analyzer {
    private Analyzer() {}

    private static final Pattern METHOD = Pattern.compile(
            "public\\s+(static\\s+)?([\\w$.\\[\\]<>,\\s]+?)\\s+([a-zA-Z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*(?:throws\\s[^{]+)?\\{");

    public static List<Sig> publicMethods(String pasted) {
        String src = stripCommentsAndStrings(pasted);
        List<Sig> out = new ArrayList<>();
        Matcher m = METHOD.matcher(src);
        while (m.find()) {
            String ret = m.group(2).strip().replaceAll("\\s+", " ");
            String name = m.group(3);
            if (name.equals("main")) continue;
            List<Sig.Param> params = new ArrayList<>();
            String raw = m.group(4).strip();
            if (!raw.isEmpty()) {
                for (String piece : splitTop(raw)) {
                    String p = piece.strip().replaceAll("\\s+", " ").replace("final ", "");
                    int sp = lastTopSpace(p);
                    if (sp < 0) continue;
                    params.add(new Sig.Param(p.substring(0, sp).strip(), p.substring(sp + 1).strip()));
                }
            }
            out.add(new Sig(name, ret, m.group(1) != null, params));
        }
        return out;
    }

    /** Split on commas not nested inside <> or []. */
    static List<String> splitTop(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '[') depth++;
            else if (c == '>' || c == ']') depth--;
            else if (c == ',' && depth == 0) { parts.add(s.substring(start, i)); start = i + 1; }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** Index of the last space at <>-depth 0 (separates type from name). */
    static int lastTopSpace(String s) {
        int depth = 0, last = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ' ' && depth == 0) last = i;
        }
        return last;
    }

    static String stripCommentsAndStrings(String src) {
        src = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        src = src.replaceAll("//[^\n]*", " ");
        src = src.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
        src = src.replaceAll("'(\\\\.|[^'\\\\])'", "' '");
        return src;
    }
}
