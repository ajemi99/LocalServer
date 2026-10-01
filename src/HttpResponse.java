import java.nio.charset.StandardCharsets;

public class HttpResponse {

    String status;
    String contentType;
    byte[] body;
    String extraHeaders = "";      // ex: "Allow: GET\r\n" ou "Location: /\r\n"

    // Pour du texte (ErrorPages, etc.)
    public HttpResponse(String status, String contentType, String body) {
        this(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    // Pour des octets (fichiers, images...)
    public HttpResponse(String status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    // Retourne la réponse HTTP complète en octets : headers (texte) + body (octets bruts)
    public byte[] build() {
        String head = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + extraHeaders
                + "\r\n";

        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[headBytes.length + body.length];
        System.arraycopy(headBytes, 0, all, 0, headBytes.length);
        System.arraycopy(body, 0, all, headBytes.length, body.length);
        return all;
    }
}