import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerConfig {

    String host = "0.0.0.0";
    List<Integer> ports = new ArrayList<>();
    String serverName = "";                        // sera utilisé avec le header Host (étape 5)
    boolean isDefault = false;                     // serveur par défaut pour ses ports
    long clientMaxBodySize = 1024 * 1024;          // en octets (défaut 1 Mo)
    Map<Integer, String> errorPages = new LinkedHashMap<>(); // 404 -> "error_pages/404.html"
    List<RouteConfig> routes = new ArrayList<>();
}
