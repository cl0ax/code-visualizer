import viz.driver.Analyzer;
import viz.model.Sig;
import java.util.List;

public class TestAnalyzer {
    public static void main(String[] a) {
        List<Sig> ms = Analyzer.publicMethods("""
            class Solution {
                // public int fake(int x) { }
                public boolean isPalindrome(String s) { return true; }
                public static int[] twoSum(int[] nums, int target) throws Exception { return null; }
                public Map<String, List<Integer>> group(Map<String, Integer> m, List<List<Integer>> xs) { return null; }
                public static void main(String[] args) { }
            }
            """);
        T.eq(ms.size(), 3, "three methods (main and commented-out skipped)");
        T.eq(ms.get(0).name(), "isPalindrome", "name");
        T.eq(ms.get(0).returnType(), "boolean", "return type");
        T.eq(ms.get(0).params().get(0).type(), "String", "param type");
        T.eq(ms.get(0).params().get(0).name(), "s", "param name");
        T.check(ms.get(1).isStatic(), "static + throws handled");
        T.eq(ms.get(1).params().get(1).name(), "target", "second param name");
        T.eq(ms.get(2).params().get(1).type(), "List<List<Integer>>", "nested generic param type");
        T.check(!ms.get(0).isStatic(), "instance method not static");
        T.done("TestAnalyzer");
    }
}
