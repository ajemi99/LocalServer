package com.localserver.routing;

import com.localserver.config.RouteConfig;
import com.localserver.config.ServerConfig;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    @TempDir
    Path tempDir;

    @Test
    void servesIndexFromConfiguredRoot() throws Exception {
        Path web = Files.createDirectories(tempDir.resolve("www"));
        Files.writeString(web.resolve("index.html"), "hello index");

        ServerConfig server = serverWithRoot(web);
        HttpResponse response = Router.route(request("GET", "/"), 8080, List.of(server));

        assertEquals("200 OK", response.status);
        assertEquals("hello index", new String(response.body, StandardCharsets.UTF_8));
    }

    @Test
    void returns405WithAllowHeader() throws Exception {
        Path web = Files.createDirectories(tempDir.resolve("www"));
        Files.writeString(web.resolve("index.html"), "hello");

        ServerConfig server = serverWithRoot(web);
        HttpResponse response = Router.route(request("POST", "/"), 8080, List.of(server));

        assertEquals("405 Method Not Allowed", response.status);
        assertEquals("Allow: GET\r\n", response.extraHeaders);
    }

    @Test
    void redirectsConfiguredRoute() {
        ServerConfig server = new ServerConfig();
        server.baseDir = tempDir;
        server.ports.add(8080);

        RouteConfig route = new RouteConfig();
        route.path = "/old";
        route.methods.add("GET");
        route.redirect = "/";
        server.routes.add(route);

        HttpResponse response = Router.route(request("GET", "/old"), 8080, List.of(server));

        assertEquals("301 Moved Permanently", response.status);
        assertEquals("Location: /\r\n", response.extraHeaders);
    }

    @Test
    void blocksPathTraversal() throws Exception {
        Path web = Files.createDirectories(tempDir.resolve("www"));
        Files.writeString(web.resolve("index.html"), "hello");
        Files.writeString(tempDir.resolve("secret.txt"), "secret");

        ServerConfig server = serverWithRoot(web);
        HttpResponse response = Router.route(request("GET", "/../secret.txt"), 8080, List.of(server));

        assertEquals("403 Forbidden", response.status);
    }

    private ServerConfig serverWithRoot(Path web) {
        ServerConfig server = new ServerConfig();
        server.baseDir = tempDir;
        server.ports.add(8080);

        RouteConfig route = new RouteConfig();
        route.path = "/";
        route.methods.add("GET");
        route.root = tempDir.relativize(web).toString();
        route.index = "index.html";
        server.routes.add(route);
        return server;
    }

    private HttpRequest request(String method, String path) {
        HttpRequest request = new HttpRequest();
        request.method = method;
        request.path = path;
        request.version = "HTTP/1.1";
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "localhost");
        request.headers = headers;
        return request;
    }
}
