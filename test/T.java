/** Tiny assertion helpers for the zero-dependency test mains. */
public final class T {
    private static int checks = 0;
    public static void check(boolean cond, String msg) {
        checks++;
        if (!cond) { System.err.println("FAIL: " + msg); System.exit(1); }
    }
    public static void eq(Object actual, Object expected, String msg) {
        check(java.util.Objects.equals(actual, expected), msg + " — expected " + expected + " but got " + actual);
    }
    public static void done(String name) { System.out.println("PASS " + name + " (" + checks + " checks)"); }
}
