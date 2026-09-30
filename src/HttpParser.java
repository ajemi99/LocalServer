import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpParser {
    
    public static HttpRequest parse(String rawRequest) {

        HttpRequest request = new HttpRequest();

        String requestLine =
                getRequestLine(rawRequest);

        String[] parts =
                requestLine.split(" ");

        request.method = parts[0];
        request.path = parts[1];
        request.version = parts[2];

        request.headers =
                parseHeaders(rawRequest);

        request.body =
                parseBody(rawRequest);

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

        Map<String, String> headers =
                new HashMap<>();

        String[] lines =
                rawRequest.split("\r\n");

        for (int i = 1; i < lines.length; i++) {

            String line = lines[i];

            if (line.isEmpty()) {
                break;
            }

            int colon =
                    line.indexOf(":");

            if (colon == -1) {
                continue;
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

        String value =
                request.headers.get("Content-Length");

        if (value == null) {
            return 0;
        }

        return Integer.parseInt(value);
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
