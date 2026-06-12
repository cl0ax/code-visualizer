package viz;

import viz.server.Server;
import viz.util.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Entry point. `serve [--open]` starts the web app; `trace <file.java> <input>...` prints trace JSON. */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("serve")) {
            Server.start(4747, Arrays.asList(args).contains("--open"));
            return;
        }
        if (args[0].equals("trace") && args.length >= 2) {
            String code;
            try {
                code = Files.readString(Path.of(args[1]));
            } catch (IOException e) {
                System.err.println("error: cannot read " + args[1] + ": " + e.getMessage());
                System.exit(1);
                return;
            }
            List<String> inputs = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
            System.out.println(Json.write(Pipeline.trace(code, inputs, null)));
            return;
        }
        System.err.println("usage: viz.Main serve [--open] | viz.Main trace <file.java> <input>...");
        System.exit(2);
    }
}
