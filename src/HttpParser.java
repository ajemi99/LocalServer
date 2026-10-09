import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class HttpParser {
    
    // Cherche la fin des headers (\r\n\r\n) dans les OCTETS. Retourne l'index, ou -1 si pas encore arrivée.
    public static int findHeaderEnd(byte[] raw) {
        return findHeaderEnd(raw, raw.length);
    }

    // Pareil, mais on ne regarde que les `len` premiers octets (le reste du tableau est vide)
    public static int findHeaderEnd(byte[] raw, int len) {
        for (int i = 0; i + 3 < len; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    // raw = tout ce qui est arrivé (headers + début ou totalité du body), en octets
    public static HttpRequest parse(byte[] raw) {

        int headerEnd = findHeaderEnd(raw);
        if (headerEnd == -1) {
            throw new BadRequestException("Headers not complete");
        }

        // Seuls les headers sont du texte. ISO_8859_1 = 1 octet -> 1 caractère, sans jamais rien casser.
        String rawRequest = new String(raw, 0, headerEnd, StandardCharsets.ISO_8859_1) + "\r\n";

        HttpRequest request = parse(rawRequest);

        // Le body reste en octets : tout ce qui suit \r\n\r\n
        request.body = java.util.Arrays.copyOfRange(raw, headerEnd + 4, raw.length);
        return request;
    }

    // Parse la partie texte (request line + headers). Le body est rempli par parse(byte[]).
    private static HttpRequest parse(String rawRequest) {

        HttpRequest request = new HttpRequest();

        String requestLine =
                getRequestLine(rawRequest);
        if(requestLine == null || requestLine.isEmpty()){
            throw  new BadRequestException("Empty request line");
        }

        String[] parts =
                requestLine.split(" ");
        if(parts.length != 3){
            throw  new BadRequestException("Malformed request line");
        }

        String method = parts[0];
        String path = parts[1];
        String version = parts[2];

        if (!method.matches("[A-Z]+")) {
            throw new BadRequestException("Bad method");
        }
        if (!path.startsWith("/")) {
            throw new BadRequestException("Bad path");
        }
        if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) {
            throw new BadRequestException("Bad HTTP version");
        }
        request.method = method;
        request.path = path;
        request.version = version;
        request.headers = parseHeaders(rawRequest);

        // HTTP/1.1 kayt-lb header Host
        if (version.equals("HTTP/1.1") && !request.headers.containsKey("Host")) {
            throw new BadRequestException("Missing Host header");
        }

        return request;
    }
    public static String getRequestLine(String rawRequest) {
        
        int endOfLine = rawRequest.indexOf("\r\n");

        if (endOfLine == -1) {
            return null;
        }

        return rawRequest.substring(0, endOfLine);
    }

    public static Map<String, String> parseHeaders(String rawRequest) {

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        String[] lines =
                rawRequest.split("\r\n");

        for (int i = 1; i < lines.length; i++) {

            String line = lines[i];

            if (line.isEmpty()) {
                break;
            }

            int colon =
                    line.indexOf(":");

            if (colon <= 0) {
                throw new BadRequestException("Malformed header: " + line);
            }

            String name =
                    line.substring(0, colon).trim();

            String value =
                    line.substring(colon + 1).trim();

            headers.put(name, value);
        }

        return headers;
    }

    public static int getContentLength(HttpRequest request) {
        String value = request.headers.get("Content-Length");
        if (value == null) {
            return 0;
        }
        try {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new BadRequestException("Negative Content-Length");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid Content-Length");
        }
    }
    
    public static boolean isBodyComplete(HttpRequest request) {

        int expected =
                getContentLength(request);

        // On compte des OCTETS (body.length), plus de conversion texte
        return request.body.length >= expected;
    }
}