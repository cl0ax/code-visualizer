import viz.driver.Inputs;

public class TestInputs {
    public static void main(String[] a) {
        T.eq(Inputs.toJava(0, "int", "5").decl().strip(), "int p0 = 5;", "int");
        T.eq(Inputs.toJava(0, "long", "5").decl().strip(), "long p0 = 5L;", "long suffix");
        T.eq(Inputs.toJava(0, "boolean", "true").decl().strip(), "boolean p0 = true;", "boolean");
        T.check(Inputs.toJava(0, "double", "2").decl().contains("2.0"), "double gets decimal");
        T.check(Inputs.toJava(0, "String", "\"a\\\"b\"").decl().contains("String p0 = \"a\\\"b\";"), "string re-escaped");
        T.check(Inputs.toJava(0, "String", "null").decl().contains("String p0 = null;"), "null string");
        T.check(Inputs.toJava(0, "char", "'x'").decl().contains("char p0 = 'x';"), "char literal");
        T.check(Inputs.toJava(1, "int[]", "[2,7]").decl().contains("new int[]{2, 7}"), "int[]");
        T.check(Inputs.toJava(0, "int[][]", "[[1,2],[3]]").decl().contains("new int[][]{new int[]{1, 2}, new int[]{3}}"), "int[][]");
        T.check(Inputs.toJava(0, "String[]", "[\"eat\",\"tea\"]").decl().contains("new String[]{\"eat\", \"tea\"}"), "String[]");
        String list = Inputs.toJava(0, "List<Integer>", "[1,2]").decl();
        T.check(list.contains("java.util.List<Integer> p0 = new java.util.ArrayList<>(java.util.Arrays.<Integer>asList(1, 2));"), "List<Integer>, got: " + list);
        String nested = Inputs.toJava(0, "List<List<Integer>>", "[[1],[2,3]]").decl();
        T.check(nested.contains("java.util.Arrays.<java.util.List<Integer>>asList("), "nested list witness, got: " + nested);
        String set = Inputs.toJava(0, "Set<Integer>", "[1]").decl();
        T.check(set.contains("new java.util.LinkedHashSet<>(java.util.Arrays.<Integer>asList(1));"), "set, got: " + set);
        String map = Inputs.toJava(0, "Map<String,Integer>", "{\"a\":1}").decl();
        T.check(map.contains("new java.util.LinkedHashMap<>();") && map.contains("p0.put(\"a\", 1);"), "map puts, got: " + map);
        try { Inputs.toJava(2, "int", "oops"); T.check(false, "bad literal should throw"); }
        catch (Inputs.InputError e) { T.eq(e.param, 2, "param index on error"); }
        try { Inputs.toJava(0, "int", "2.5"); T.check(false, "double into int should throw"); }
        catch (Inputs.InputError e) { T.check(e.getMessage().contains("integer"), "clear message"); }
        T.done("TestInputs");
    }
}
