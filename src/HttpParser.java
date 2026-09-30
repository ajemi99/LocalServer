import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class HttpParser {
    
    public static HttpRequest parse(String rawRequest) {

        HttpRequest request = new HttpRequest();

        String requestLine =
                getRequestLine(rawRequest);
        if(requestLine == null || requestLine == null){
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
        request.body =parseBody(rawRequest);

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
    public static String parseBody(String rawRequest) {

        int bodyStart =
                rawRequest.indexOf("\r\n\r\n");

        if (bodyStart == -1) {
            return "";
        }

        bodyStart += 4;

        return rawRequest.substring(bodyStart);
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

        int received =
                request.body
                        .getBytes(StandardCharsets.UTF_8)
                        .length;

        return received >= expected;
    }
}
