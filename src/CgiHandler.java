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
            env.put("REDIRECT_STATUS", "200");
            env.put("PYTHONIOENCODING", "utf-8"); // Force Python à utiliser UTF-8 sous Windows

            int q = request.path.indexOf('?');
            env.put("QUERY_STRING", q != -1 ? request.path.substring(q + 1) : "");

            // Extraction insensible à la casse des en-têtes HTTP
            if (request.headers != null) {
                for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("Content-Length")) {
                        env.put("CONTENT_LENGTH", entry.getValue());
                    }
                    if (entry.getKey().equalsIgnoreCase("Content-Type")) {
                        env.put("CONTENT_TYPE", entry.getValue());
                    }
                }
            }

                Process process = pb.start();

                // 2. Si c'est un POST, transmettre le corps au script (stdin)
                if ("POST".equalsIgnoreCase(request.method) && request.body != null && !request.body.isEmpty()) {
                    try (OutputStream os = process.getOutputStream()) {
                        os.write(request.body.getBytes(StandardCharsets.UTF_8));
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

                // 4. Découper les en-têtes et le corps de manière robuste
                String rawOutput = new String(cgiOutput, StandardCharsets.UTF_8);

                // Normalisation des fins de ligne Windows/Linux
                String normalized = rawOutput.replace("\r\n", "\n").replace("\r", "\n");
                int headerEnd = normalized.indexOf("\n\n");

                if (headerEnd == -1) {
                    // Aucun en-tête CGI détecté
                    return new HttpResponse("200 OK", "text/html; charset=UTF-8", cgiOutput);
                }

                String headersPart = normalized.substring(0, headerEnd);
                byte[] bodyPart = normalized.substring(headerEnd + 2).getBytes(StandardCharsets.UTF_8);

                HttpResponse response = new HttpResponse("200 OK", "text/html; charset=UTF-8", bodyPart);

                // Traitement des en-têtes retournés par le script
                for (String line : headersPart.split("\n")) {
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