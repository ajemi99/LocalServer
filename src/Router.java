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
                return serveStatic(request, path, rawPath, route, server);
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
        byte[] bodyBytes = request.body != null ? request.body : new byte[0];

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

        // 3. Exécution CGI si le fichier cible est un script CGI
        if (Files.isRegularFile(target)) {
            String fileName = target.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            if (dot != -1) {
                String ext = fileName.substring(dot);
                if (route.cgi != null && route.cgi.containsKey(ext)) {
                    String interpreter = route.cgi.get(ext);
                    return CgiHandler.execute(request, target, interpreter, server);
                }
            }
        }

        // 4. Upload de fichier depuis un formulaire (multipart/form-data) vers un DOSSIER
        String contentType = request.headers.get("Content-Type");
        boolean isFolder = Files.isDirectory(target) || path.endsWith("/");
        if (isFolder && contentType != null
                && contentType.toLowerCase().startsWith("multipart/form-data")) {
            return handleMultipart(request, contentType, target, path, server);
        }

        // Un POST standard (non CGI, non multipart) ne peut pas être fait directement sur un dossier
        if (isFolder) {
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
            response.extraHeaders = "Location: " + encodePath(path) + "\r\n";
            return response;
        } else {
            return new HttpResponse("200 OK", "text/plain; charset=UTF-8", "File updated successfully\n");
        }
    }

    // =====================================================
    // Upload multipart : enregistre chaque fichier du formulaire dans le dossier cible
    // =====================================================
    private static HttpResponse handleMultipart(HttpRequest request, String contentType,
                                               Path dir, String path, ServerConfig server) throws IOException {

        if (!Files.isDirectory(dir)) {
            return error(404, server);
        }

        String boundary = MultipartParser.boundaryOf(contentType);
        if (boundary == null) {
            throw new BadRequestException("Missing multipart boundary");
        }

        List<MultipartParser.Part> parts = MultipartParser.parse(request.body, boundary);

        String base = encodePath(path.endsWith("/") ? path : path + "/");   // encodé : sûr dans un header et dans un lien
        StringBuilder rows = new StringBuilder();
        String firstLocation = null;
        boolean created = false;
        int count = 0;

        for (MultipartParser.Part part : parts) {

            // Les champs de texte du formulaire (sans fichier) sont ignorés ;
            // un champ fichier laissé vide par le navigateur a filename="" : ignoré aussi.
            if (part.filename == null || part.filename.isEmpty()) {
                continue;
            }

            String name = safeFileName(part.filename);
            Path dest = dir.resolve(name).normalize();

            // Ceinture et bretelles : on ne sort jamais du dossier, et on n'écrase pas un dossier
            if (!dest.startsWith(dir) || Files.isDirectory(dest)) {
                return error(403, server);
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

    // "/mon dossier/a\r\nb" -> "/mon%20dossier/a%0D%0Ab"  : un chemin décodé ne doit JAMAIS aller tel quel dans un header
    static String encodePath(String p) {
        String[] segments = p.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    // Nom de fichier reçu du client = DONNÉE NON FIABLE. On le nettoie avant de s'en servir.
    //   "../../etc/x"  -> "x"        "a<b>.txt" -> "a_b_.txt"        "CON.txt" -> "_CON.txt"
    static String safeFileName(String raw) {
        String n = raw;

        // Garder uniquement le dernier morceau (certains navigateurs envoient C:\dossier\photo.png)
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);

        // Caractères de contrôle et caractères interdits sous Windows -> "_"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            sb.append((c < 0x20 || c == 0x7f || "<>:\"|?*".indexOf(c) >= 0) ? '_' : c);
        }
        n = sb.toString().trim();

        // Pas de points au début (".." , ".htaccess") ni de points/espaces à la fin (Windows les retire)
        while (n.startsWith(".")) n = n.substring(1);
        while (n.endsWith(".") || n.endsWith(" ")) n = n.substring(0, n.length() - 1);

        if (n.length() > 200) n = n.substring(0, 200);
        if (n.isEmpty()) n = "upload.bin";

        // Noms réservés de Windows (CON, NUL, COM1...) : "CON.txt" ouvrirait un périphérique, pas un fichier
        String stem = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
        if (stem.toUpperCase().matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) n = "_" + n;

        return n;
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
    static ServerConfig selectServer(HttpRequest request, int port, List<ServerConfig> servers) {

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
                host = host.substring(0, colon);
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

    private static String prefix(String p) {
        return (p.length() > 1 && p.endsWith("/")) ? p.substring(0, p.length() - 1) : p;
    }

    private static boolean matches(String path, String routePath) {
        String p = prefix(routePath);
        if (p.equals("/")) {
            return true;
        }
        return path.equals(p) || path.startsWith(p + "/");
    }

    // =====================================================
    // Fichiers statiques et CGI
    // =====================================================
    private static HttpResponse serveStatic(HttpRequest request, String path, String rawPath,
                                            RouteConfig route, ServerConfig server) throws IOException {

        Path root = Path.of(route.root).toAbsolutePath().normalize();
        String routePrefix = prefix(route.path);

        String relative = routePrefix.equals("/") ? path : path.substring(routePrefix.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        // Cas "root" = un fichier précis
        if (Files.isRegularFile(root)) {
            return relative.isEmpty() ? serveFile(root, server) : error(404, server);
        }

        Path target = root.resolve(relative).normalize();

        // SÉCURITÉ : protection contre "Path Traversal" (../)
        if (!target.startsWith(root)) {
            return error(403, server);
        }

        if (!Files.exists(target)) {
            return error(404, server);
        }

        // Cas dossier
        if (Files.isDirectory(target)) {

            if (!rawPath.endsWith("/")) {
                return redirect(rawPath + "/");
            }

            if (route.index != null) {
                Path indexFile = target.resolve(route.index).normalize();
                if (Files.isRegularFile(indexFile)) {
                    // Vérification si le fichier index est un script CGI
                    String fileName = indexFile.getFileName().toString();
                    int dot = fileName.lastIndexOf('.');
                    if (dot != -1) {
                        String ext = fileName.substring(dot);
                        if (route.cgi != null && route.cgi.containsKey(ext)) {
                            String interpreter = route.cgi.get(ext);
                            return CgiHandler.execute(request, indexFile, interpreter, server);
                        }
                    }
                    return serveFile(indexFile, server);
                }
            }

            if (route.directoryListing) {
                return listing(target, path);
            }

            return error(403, server);
        }

        // Cas fichier régulier : vérification CGI
        if (Files.isRegularFile(target)) {
            String fileName = target.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            if (dot != -1) {
                String ext = fileName.substring(dot);
                if (route.cgi != null && route.cgi.containsKey(ext)) {
                    String interpreter = route.cgi.get(ext);
                    return CgiHandler.execute(request, target, interpreter, server);
                }
            }
            return serveFile(target, server);
        }

        return error(403, server);
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

    static HttpResponse error(int code, ServerConfig server) {
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
            }
        }
        return def;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}