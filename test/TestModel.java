import viz.model.Sig;
import viz.model.Trace;
import viz.util.Json;
import java.util.*;

public class TestModel {
    public static void main(String[] a) {
        Sig sig = new Sig("isPalindrome", "boolean", false, List.of(new Sig.Param("String", "s")));
        T.eq(Json.write(sig),
             "{\"name\":\"isPalindrome\",\"returnType\":\"boolean\",\"isStatic\":false,"
           + "\"params\":[{\"type\":\"String\",\"name\":\"s\"}]}", "sig json");
        Trace.Step step = new Trace.Step(0, 4, Map.of(), List.of("l"), List.of());
        T.eq(Json.write(step), "{\"i\":0,\"line\":4,\"locals\":{},\"changed\":[\"l\"],\"stdout\":[]}", "step json");
        Trace.Result res = new Trace.Result("return", null, "boolean", null, 7);
        T.check(Json.write(res).contains("\"kind\":\"return\""), "result json");
        T.done("TestModel");
    }
}
