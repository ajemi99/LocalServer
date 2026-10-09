import java.util.Map;

public class HttpRequest {

    String method;
    String path;
    String version;

    Map<String, String> headers;

    // Octets bruts, jamais convertis en texte (sinon les fichiers binaires sont abîmés)
    byte[] body = new byte[0];
}