package viz.server;

import com.sun.net.httpserver.*;
import viz.Pipeline;
import viz.util.Json;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Zero-dependency HTTP server: static files from web/, POST /api/analyze, POST /api/trace. */
public final class Server {
    private Server() {}

    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png");

    public static void start(int preferredPort, boolean open) throws IOException {
        Path webRoot = Path.of("web").toAbsolutePath().normalize();
        if (!Files.isDirectory(webRoot))
            throw new IOException("web/ not found at " + webRoot + " — run from the repo root");

        HttpServer server = null;
        int port = preferredPort;
        for (; port < preferredPort + 10; port++) {
            try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0); break; }
            catch (IOException busy) { /* try the next port */ }
        }
        if (server == null) throw new IOException("no free port near " + preferredPort);

        server.createContext("/", ex -> serveStatic(ex, webRoot));
        server.createContext("/api/analyze", ex -> api(ex,
                body -> Pipeline.analyze((String) body.get("code"))));
        server.createContext("/api/trace", ex -> api(ex, body -> {
            @SuppressWarnings("unchecked")
            List<String> inputs = (List<String>) (List<?>) body.getOrDefault("inputs", List.of());
            return Pipeline.trace((String) body.get("code"), inputs, (String) body.get("method"));
        }));
        server.setExecutor(Executors.newFixedThreadPool(4));  // non-daemon: keeps the JVM alive
        server.start();
        String url = "http://localhost:" + port;
        System.out.println("NeetCode Visualizer running at " + url + "  (Ctrl-C to stop)");
        if (open) try { new ProcessBuilder("open", url).start(); } catch (Exception ignore) {}
    }

    private interface Api { Object handle(Map<String, Object> body) throws Exception; }

    private static void api(HttpExchange ex, Api fn) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            send(ex, 405, "{\"ok\":false,\"error\":\"POST only\"}");
            return;
        }
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) Json.parse(body);
            send(ex, 200, Json.write(fn.handle(json)));
        } catch (Exception e) {
            send(ex, 200, Json.write(Map.of("ok", false, "error", String.valueOf(e))));
        }
    }

    private static void serveStatic(HttpExchange ex, Path webRoot) throws IOException {
        String raw = ex.getRequestURI().getPath();
        if (raw.equals("/")) raw = "/index.html";
        Path file = webRoot.resolve(raw.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) { send(ex, 404, "not found"); return; }
        String name = file.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1);
        ex.getResponseHeaders().set("Content-Type", MIME.getOrDefault(ext, "application/octet-stream"));
        byte[] bytes = Files.readAllBytes(file);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!ex.getResponseHeaders().containsKey("Content-Type"))
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
