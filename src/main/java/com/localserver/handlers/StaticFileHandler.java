package com.localserver.handlers;

import com.localserver.config.RouteConfig;
import com.localserver.config.ServerConfig;
import com.localserver.http.ErrorPages;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;
import com.localserver.constants.Constants;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Serves static files, directory indexes and CGI scripts for GET requests.
 */
public final class StaticFileHandler {

    private StaticFileHandler() {
    }

    public static HttpResponse handle(HttpRequest request, String path, String rawPath,
                                      String relative, RouteConfig route, ServerConfig server)
            throws IOException {

        Path root = server.resolvePath(route.root);

        // Case "root" = one specific file
        if (Files.isRegularFile(root)) {
            return relative.isEmpty() ? serveFile(root, server) : ErrorPages.get(404, server);
        }

        Path target = root.resolve(relative).normalize();

        // SECURITY: protection against path traversal (../)
        if (!target.startsWith(root)) {
            return ErrorPages.get(403, server);
        }

        if (!Files.exists(target)) {
            return ErrorPages.get(404, server);
        }

        // Directory case
        if (Files.isDirectory(target)) {

            if (!rawPath.endsWith("/")) {
                return HttpResponse.redirect(rawPath + "/");
            }

            if (route.index != null) {
                Path indexFile = target.resolve(route.index).normalize();
                if (Files.isRegularFile(indexFile)) {
                    // Is the index file a CGI script?
                    String fileName = indexFile.getFileName().toString();
                    int dot = fileName.lastIndexOf('.');
                    if (dot != -1) {
                        String ext = fileName.substring(dot);
                        if (route.cgi != null && route.cgi.containsKey(ext)) {
                            String interpreter = route.cgi.get(ext);
                            return CGIHandler.execute(request, indexFile, interpreter, server);
                        }
                    }
                    return serveFile(indexFile, server);
                }
            }

            if (route.directoryListing) {
                return listing(target, path);
            }

            return ErrorPages.get(403, server);
        }

        // Regular file: CGI check
        if (Files.isRegularFile(target)) {
            String fileName = target.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            if (dot != -1) {
                String ext = fileName.substring(dot);
                if (route.cgi != null && route.cgi.containsKey(ext)) {
                    String interpreter = route.cgi.get(ext);
                    return CGIHandler.execute(request, target, interpreter, server);
                }
            }
            return serveFile(target, server);
        }

        return ErrorPages.get(403, server);
    }

    private static HttpResponse serveFile(Path file, ServerConfig server) throws IOException {
        if (!Files.isReadable(file)) {
            return ErrorPages.get(403, server);
        }
        byte[] data = Files.readAllBytes(file);
        return new HttpResponse(Constants.Http.DEFAULT_STATUS, mimeType(file), data);
    }

    private static String mimeType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return Constants.Mime.DEFAULT;
        }
        return Constants.Mime.TYPES.getOrDefault(name.substring(dot + 1), Constants.Mime.DEFAULT);
    }

    // Directory listing
    private static HttpResponse listing(Path dir, String path) throws IOException {

        List<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(entries::add);
        }
        entries.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Index of ")
          .append(escape(path)).append("</title></head><body style=\"font-family:sans-serif\">")
          .append("<h1>Index of ").append(escape(path)).append("</h1><ul>");

        if (!path.equals("/")) {
            sb.append("<li><a href=\"../\">../</a></li>");
        }

        for (Path p : entries) {
            String name = p.getFileName().toString();
            boolean isDir = Files.isDirectory(p);
            String href = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20")
                    + (isDir ? "/" : "");
            sb.append("<li><a href=\"").append(href).append("\">")
              .append(escape(name)).append(isDir ? "/" : "").append("</a></li>");
        }

        sb.append("</ul><hr><small>LocalServer</small></body></html>");
        return new HttpResponse(Constants.Http.DEFAULT_STATUS, Constants.Http.CONTENT_TYPE_HTML, sb.toString());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
