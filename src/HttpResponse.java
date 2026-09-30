import java.nio.charset.StandardCharsets;

public class HttpResponse {

    String status;
    String contentType;
    String body;
    String extraHeaders = "";

    public HttpResponse(
            String status,
            String contentType,
            String body) {

        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    public String build() {

        return "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " +
                body.getBytes(StandardCharsets.UTF_8).length +
                "\r\n" +
                extraHeaders +
                "\r\n" +
                body;
    }
}
