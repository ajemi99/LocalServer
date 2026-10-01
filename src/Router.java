import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Router {

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "application/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf")
    );

    // =====================================================
    // Point d'entrée
    // =====================================================
    public static HttpResponse route(HttpRequest request, int port, List<ServerConfig> servers) {

        // 1. Quel serveur (de la config) répond ?
        ServerConfig server = selectServer(request, port, servers);
        if (server == null) {
            return ErrorPages.get(500);
        }

        // 2. Nettoyer le path : enlever "?query" et décoder les %XX
        String rawPath = request.path;
        int q = rawPath.indexOf('?');
        if (q != -1) {
            rawPath = rawPath.substring(0, q);
        }

        String path;
        try {
            // "+" reste "+" dans un path (il ne veut pas dire espace)
            path = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Bad percent-encoding");
        }
        if (path.indexOf('\0') != -1) {
            throw new BadRequestException("Null byte in path");
        }

        // 3. Quelle route ?
        RouteConfig route = findRoute(path, server);
        if (route == null) {
            return error(404, server);
        }

        // 4. Méthode autorisée ?
        if (!route.methods.contains(request.method)) {
            HttpResponse r = error(405, server);
            r.extraHeaders = "Allow: " + String.join(", ", route.methods) + "\r\n";
            return r;
        }

        // 5. Redirection ?
        if (route.redirect != null) {
            return redirect(route.redirect);
        }

    // 6. Traitement selon la méthode HTTP
        try {
            if (request.method.equals("GET")) {
                return serveStatic(path, rawPath, route, server);
            } else if (request.method.equals("POST")) {
                return handlePost(request, path, route, server);
            } else if (request.method.equals("DELETE")) {
                return handleDelete(path, route, server);
            } else {
                HttpResponse r = error(405, server);
                r.extraHeaders = "Allow: " + String.join(", ", route.methods) + "\r\n";
                return r;
            }
        } catch (InvalidPathException e) {
            throw new BadRequestException("Invalid path");
        } catch (IOException e) {
            return error(500, server);
        }
    }

    // =====================================================
    // Gestion de la méthode POST
    // =====================================================
    private static HttpResponse handlePost(HttpRequest request, String path, RouteConfig route, ServerConfig server) throws IOException {
        byte[] bodyBytes = request.body.getBytes(StandardCharsets.UTF_8);

        // 1. Validation de clientMaxBodySize
        if (bodyBytes.length > server.clientMaxBodySize) {
            return error(413, server);
        }

        Path root = Path.of(route.root).toAbsolutePath().normalize();
        String routePrefix = prefix(route.path);
        String relative = routePrefix.equals("/") ? path : path.substring(routePrefix.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        Path target = root.resolve(relative).normalize();

        // 2. Sécurité : vérification Path Traversal
        if (!target.startsWith(root)) {
            return error(403, server);
        }

        // Un POST ne peut pas être fait directement sur un dossier sans préciser de nom de fichier
        if (Files.isDirectory(target) || path.endsWith("/")) {
            return error(400, server);
        }

        boolean exists = Files.exists(target);

        // Création des dossiers parents si nécessaire
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }

        // Écriture des données dans le fichier
        Files.write(target, bodyBytes);

        if (!exists) {
            HttpResponse response = new HttpResponse("201 Created", "text/plain; charset=UTF-8", "File created successfully\n");
            response.extraHeaders = "Location: " + path + "\r\n";
            return response;
        } else {
            return new HttpResponse("200 OK", "text/plain; charset=UTF-8", "File updated successfully\n");
        }
    }

    // =====================================================
    // Gestion de la méthode DELETE
    // =====================================================
    private static HttpResponse handleDelete(String path, RouteConfig route, ServerConfig server) throws IOException {
        Path root = Path.of(route.root).toAbsolutePath().normalize();
        String routePrefix = prefix(route.path);
        String relative = routePrefix.equals("/") ? path : path.substring(routePrefix.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        Path target = root.resolve(relative).normalize();

        // 1. Sécurité : vérification Path Traversal
        if (!target.startsWith(root)) {
            return error(403, server);
        }

        // 2. Vérification d'existence
        if (!Files.exists(target)) {
            return error(404, server);
        }

        // 3. Interdiction de supprimer des répertoires
        if (Files.isDirectory(target)) {
            return error(403, server);
        }

        // 4. Suppression du fichier
        try {
            Files.delete(target);
            return new HttpResponse("200 OK", "text/html; charset=UTF-8",
                    "<!DOCTYPE html><html><body><h1>File Deleted</h1><p>" 
                    + escape(path) + "</p></body></html>");
        } catch (IOException e) {
            return error(500, server);
        }
    }
    

    // =====================================================
    // Choix du serveur : port + header Host
    // =====================================================
    private static ServerConfig selectServer(HttpRequest request, int port, List<ServerConfig> servers) {

        List<ServerConfig> candidates = new ArrayList<>();
        for (ServerConfig s : servers) {
            if (s.ports.contains(port)) {
                candidates.add(s);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        String host = request.headers.get("Host");
        if (host != null) {
            int colon = host.indexOf(':');
            if (colon != -1) {
                host = host.substring(0, colon);   // "localhost:8080" -> "localhost"
            }
            for (ServerConfig s : candidates) {
                if (!s.serverName.isEmpty() && s.serverName.equalsIgnoreCase(host)) {
                    return s;
                }
            }
        }

        for (ServerConfig s : candidates) {
            if (s.isDefault) {
                return s;
            }
        }
        return candidates.get(0);
    }

    // =====================================================
    // Choix de la route : le préfixe le plus long gagne
    // =====================================================
    private static RouteConfig findRoute(String path, ServerConfig server) {
        RouteConfig best = null;
        for (RouteConfig r : server.routes) {
            if (matches(path, r.path)
                    && (best == null || prefix(r.path).length() > prefix(best.path).length())) {
                best = r;
            }
        }
        return best;
    }

    // "/uploads/" -> "/uploads"   ("/" reste "/")
    private static String prefix(String p) {
        return (p.length() > 1 && p.endsWith("/")) ? p.substring(0, p.length() - 1) : p;
    }

    // "/uploads" correspond à "/uploads" et "/uploads/x", mais PAS à "/uploads2"
    private static boolean matches(String path, String routePath) {
        String p = prefix(routePath);
        if (p.equals("/")) {
            return true;
        }
        return path.equals(p) || path.startsWith(p + "/");
    }

    // =====================================================
    // Fichiers statiques
    // =====================================================
    private static HttpResponse serveStatic(String path, String rawPath,
                                            RouteConfig route, ServerConfig server) throws IOException {

        Path root = Path.of(route.root).toAbsolutePath().normalize();
        String routePrefix = prefix(route.path);

        // la partie du path après le préfixe de la route : "/uploads/a.txt" -> "a.txt"
        String relative = routePrefix.equals("/") ? path : path.substring(routePrefix.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        // Cas "root" = un fichier précis
        if (Files.isRegularFile(root)) {
            return relative.isEmpty() ? serveFile(root, server) : error(404, server);
        }

        Path target = root.resolve(relative).normalize();

        // SÉCURITÉ : on ne sort jamais du dossier root (protège contre "../")
        if (!target.startsWith(root)) {
            return error(403, server);
        }

        if (!Files.exists(target)) {
            return error(404, server);
        }

        if (Files.isDirectory(target)) {

            // "/uploads" -> redirige vers "/uploads/" (sinon les liens relatifs cassent)
            if (!rawPath.endsWith("/")) {
                return redirect(rawPath + "/");
            }

            if (route.index != null) {
                Path indexFile = target.resolve(route.index).normalize();
                if (Files.isRegularFile(indexFile)) {
                    return serveFile(indexFile, server);
                }
            }

            if (route.directoryListing) {
                return listing(target, path);
            }

            return error(403, server);
        }

        return serveFile(target, server);
    }

    private static HttpResponse serveFile(Path file, ServerConfig server) throws IOException {
        if (!Files.isReadable(file)) {
            return error(403, server);
        }
        byte[] data = Files.readAllBytes(file);
        return new HttpResponse("200 OK", mimeType(file), data);
    }

    private static String mimeType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return "application/octet-stream";
        }
        return MIME.getOrDefault(name.substring(dot + 1), "application/octet-stream");
    }

    // =====================================================
    // Listing de dossier
    // =====================================================
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
        return new HttpResponse("200 OK", "text/html; charset=UTF-8", sb.toString());
    }

    // =====================================================
    // Redirection + erreurs
    // =====================================================
    private static HttpResponse redirect(String location) {
        HttpResponse r = new HttpResponse("301 Moved Permanently", "text/html; charset=UTF-8",
                "<html><body>Moved to <a href=\"" + escape(location) + "\">"
                        + escape(location) + "</a></body></html>");
        r.extraHeaders = "Location: " + location + "\r\n";
        return r;
    }

    // Page d'erreur personnalisée (config) si elle existe, sinon page par défaut
    private static HttpResponse error(int code, ServerConfig server) {
        HttpResponse def = ErrorPages.get(code);

        String file = server.errorPages.get(code);
        if (file != null) {
            try {
                Path p = Path.of(file);
                if (Files.isRegularFile(p)) {
                    return new HttpResponse(def.status, "text/html; charset=UTF-8",
                            Files.readAllBytes(p));
                }
            } catch (Exception ignored) {
                // fichier illisible : on retombe sur la page par défaut
            }
        }
        return def;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}