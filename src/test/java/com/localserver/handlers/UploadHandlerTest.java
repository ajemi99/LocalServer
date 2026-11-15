package com.localserver.handlers;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void writesPostBodyIntoConfiguredRoot() throws Exception {
        ServerConfig server = new ServerConfig();
        server.baseDir = tempDir;

        RouteConfig route = new RouteConfig();
        route.path = "/upload";
        route.methods.add("POST");
        route.root = tempDir.toString();

        HttpRequest request = new HttpRequest();
        request.method = "POST";
        request.path = "/upload/file.txt";
        request.version = "HTTP/1.1";
        request.headers = new HashMap<>(Map.of("Host", "localhost"));
        request.body = "payload".getBytes(StandardCharsets.UTF_8);

        HttpResponse response = UploadHandler.handlePost(request, request.path, "file.txt", route, server);

        assertEquals("201 Created", response.status);
        assertEquals("payload", Files.readString(tempDir.resolve("file.txt")));
    }

    @Test
    void sanitizesClientFileNames() {
        assertEquals("x", UploadHandler.safeFileName("../../etc/x"));
        assertEquals("_CON.txt", UploadHandler.safeFileName("CON.txt"));
        assertEquals("upload.bin", UploadHandler.safeFileName(".."));
    }

    @Test
    void encodesPathSegmentsForHeaders() {
        assertEquals("/a%20b/c%0D%0Ad", UploadHandler.encodePath("/a b/c\r\nd"));
    }
}
