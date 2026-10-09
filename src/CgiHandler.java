import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

public class CgiHandler {

    public static HttpResponse execute(HttpRequest request, Path scriptPath, String interpreter, ServerConfig server) {
        try {
            ProcessBuilder pb = new ProcessBuilder(interpreter, scriptPath.toAbsolutePath().toString());

            // 1. Définition des variables d'environnement CGI
            Map<String, String> env = pb.environment();
            env.put("REQUEST_METHOD", request.method);
            env.put("SERVER_PROTOCOL", request.version);
            env.put("REDIRECT_STATUS", "200"); // Requis par certains scripts PHP/Python

            int q = request.path.indexOf('?');
            if (q != -1) {
                env.put("QUERY_STRING", request.path.substring(q + 1));
            } else {
                env.put("QUERY_STRING", "");
            }

            if (request.headers.containsKey("Content-Length")) {
                env.put("CONTENT_LENGTH", request.headers.get("Content-Length"));
            }
            if (request.headers.containsKey("Content-Type")) {
                env.put("CONTENT_TYPE", request.headers.get("Content-Type"));
            }

            Process process = pb.start();

            // 2. Si c'est un POST, transmettre le corps au script (stdin)
            if ("POST".equalsIgnoreCase(request.method) && request.body != null && request.body.length > 0) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(request.body);
                    os.flush();
                }
            }

            // 3. Lire le résultat du script (stdout)
            InputStream is = process.getInputStream();
            byte[] cgiOutput = is.readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return ErrorPages.get(500);
            }

            // 4. Séparer les en-têtes CGI du corps de la réponse
            String rawOutput = new String(cgiOutput, StandardCharsets.UTF_8);
            int headerEnd = rawOutput.indexOf("\r\n\r\n");
            int delimiterLength = 4;

            if (headerEnd == -1) {
                headerEnd = rawOutput.indexOf("\n\n");
                delimiterLength = 2;
            }

            if (headerEnd == -1) {
                // Si le script ne génère pas d'en-tête, retourner le texte brut
                return new HttpResponse("200 OK", "text/html; charset=UTF-8", cgiOutput);
            }

            String headersPart = rawOutput.substring(0, headerEnd);
            byte[] bodyPart = rawOutput.substring(headerEnd + delimiterLength).getBytes(StandardCharsets.UTF_8);

            String contentType = "text/html; charset=UTF-8";
            HttpResponse response = new HttpResponse("200 OK", contentType, bodyPart);

            // Parser les en-têtes du script (ex: Content-Type)
            for (String line : headersPart.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    if (name.equalsIgnoreCase("Content-Type")) {
                        response.contentType = value;
                    } else if (name.equalsIgnoreCase("Status")) {
                        response.status = value;
                    } else {
                        response.extraHeaders += name + ": " + value + "\r\n";
                    }
                }
            }

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return ErrorPages.get(500);
        }
    }
}