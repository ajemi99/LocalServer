package com.localserver.handlers;

import com.localserver.config.RouteConfig;
import com.localserver.config.ServerConfig;
import com.localserver.http.BadRequestException;
import com.localserver.http.ErrorPages;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;
import com.localserver.http.MultipartParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Handles POST uploads/writes and DELETE requests.
 */
public final class UploadHandler {

    private UploadHandler() {
    }

    // =====================================================
    // POST
    // =====================================================
    public static HttpResponse handlePost(HttpRequest request, String path, String relative,
                                          RouteConfig route, ServerConfig server) throws IOException {
        byte[] bodyBytes = request.body != null ? request.body : new byte[0];

        // 1. Validate clientMaxBodySize
        if (bodyBytes.length > server.clientMaxBodySize) {
            return ErrorPages.get(413, server);
        }

        Path root = server.resolvePath(route.root);
        Path target = root.resolve(relative).normalize();

        // 2. SECURITY: path traversal check
        if (!target.startsWith(root)) {
            return ErrorPages.get(403, server);
        }

        // 3. Run CGI if the target file is a CGI script
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
        }

        // 4. Form file upload (multipart/form-data) to a DIRECTORY
        String contentType = request.headers.get("Content-Type");
        boolean isFolder = Files.isDirectory(target) || path.endsWith("/");
        if (isFolder && contentType != null
                && contentType.toLowerCase().startsWith("multipart/form-data")) {
            return handleMultipart(request, contentType, target, path, server);
        }

        // A standard POST (non-CGI, non-multipart) cannot target a directory
        if (isFolder) {
            return ErrorPages.get(400, server);
        }

        boolean exists = Files.exists(target);

        // Create parent directories when necessary
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }

        // Write the data to the file
        Files.write(target, bodyBytes);

        if (!exists) {
            HttpResponse response = new HttpResponse("201 Created", "text/plain; charset=UTF-8", "File created successfully\n");
            response.extraHeaders = "Location: " + encodePath(path) + "\r\n";
            return response;
        } else {
            return new HttpResponse("200 OK", "text/plain; charset=UTF-8", "File updated successfully\n");
        }
    }

    // =====================================================
    // Multipart upload: save each file of the form into the target directory
    // =====================================================
    private static HttpResponse handleMultipart(HttpRequest request, String contentType,
                                                Path dir, String path, ServerConfig server) throws IOException {

        if (!Files.isDirectory(dir)) {
            return ErrorPages.get(404, server);
        }

        String boundary = MultipartParser.boundaryOf(contentType);
        if (boundary == null) {
            throw new BadRequestException("Missing multipart boundary");
        }

        List<MultipartParser.Part> parts = MultipartParser.parse(request.body, boundary);

        String base = encodePath(path.endsWith("/") ? path : path + "/");   // safe in a header and a link
        StringBuilder rows = new StringBuilder();
        String firstLocation = null;
        boolean created = false;
        int count = 0;

        for (MultipartParser.Part part : parts) {

            // Text fields (without file) are ignored; an empty browser file field
            // has filename="" and is ignored too.
            if (part.filename == null || part.filename.isEmpty()) {
                continue;
            }

            String name = safeFileName(part.filename);
            Path dest = dir.resolve(name).normalize();

            // Never leave the directory, and never overwrite a directory
            if (!dest.startsWith(dir) || Files.isDirectory(dest)) {
                return ErrorPages.get(403, server);
            }

            boolean existed = Files.exists(dest);
            Files.write(dest, part.data);
            if (!existed) created = true;
            count++;

            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            if (firstLocation == null) firstLocation = base + encoded;

            rows.append("<li><a href=\"").append(base).append(encoded).append("\">")
                .append(escape(name)).append("</a> (").append(part.data.length).append(" octets)</li>");
        }

        if (count == 0) {
            throw new BadRequestException("No file in upload");
        }

        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Upload</title></head>"
                + "<body style=\"font-family:sans-serif\"><h1>Upload termin\u00e9</h1><ul>" + rows
                + "</ul><p><a href=\"" + base + "\">Voir le dossier</a></p></body></html>";

        HttpResponse r = new HttpResponse(created ? "201 Created" : "200 OK", "text/html; charset=UTF-8", html);
        r.extraHeaders = "Location: " + firstLocation + "\r\n";
        return r;
    }

    // "/mon dossier/a\r\nb" -> "/mon%20dossier/a%0D%0Ab": a decoded path must
    // NEVER be placed as-is in a header.
    public static String encodePath(String p) {
        String[] segments = p.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    // A client filename is UNTRUSTED DATA. Clean it before using it.
    //   "../../etc/x"  -> "x"        "a<b>.txt" -> "a_b_.txt"        "CON.txt" -> "_CON.txt"
    public static String safeFileName(String raw) {
        String n = raw;

        // Keep only the last component (some browsers send C:\folder\photo.png)
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);

        // Control and forbidden Windows characters -> "_"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            sb.append((c < 0x20 || c == 0x7f || "<>:\"|?*".indexOf(c) >= 0) ? '_' : c);
        }
        n = sb.toString().trim();

        // No leading dots ("..", ".htaccess") or trailing dots/spaces (Windows removes them)
        while (n.startsWith(".")) n = n.substring(1);
        while (n.endsWith(".") || n.endsWith(" ")) n = n.substring(0, n.length() - 1);

        if (n.length() > 200) n = n.substring(0, 200);
        if (n.isEmpty()) n = "upload.bin";

        // Windows reserved names (CON, NUL, COM1...): "CON.txt" would open a device
        String stem = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
        if (stem.toUpperCase().matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) n = "_" + n;

        return n;
    }

    // =====================================================
    // DELETE
    // =====================================================
    public static HttpResponse handleDelete(String path, String relative,
                                            RouteConfig route, ServerConfig server) throws IOException {
        Path root = server.resolvePath(route.root);
        Path target = root.resolve(relative).normalize();

        // 1. SECURITY: path traversal check
        if (!target.startsWith(root)) {
            return ErrorPages.get(403, server);
        }

        // 2. Existence check
        if (!Files.exists(target)) {
            return ErrorPages.get(404, server);
        }

        // 3. Directories cannot be deleted
        if (Files.isDirectory(target)) {
            return ErrorPages.get(403, server);
        }

        // 4. Delete the file
        try {
            Files.delete(target);
            return new HttpResponse("200 OK", "text/html; charset=UTF-8",
                    "<!DOCTYPE html><html><body><h1>File Deleted</h1><p>"
                    + escape(path) + "</p></body></html>");
        } catch (IOException e) {
            return ErrorPages.get(500, server);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
