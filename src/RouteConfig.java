import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteConfig {

    String path;                                   // ex: "/", "/uploads"
    List<String> methods = new ArrayList<>();      // ex: ["GET", "POST"]
    String redirect;                               // ex: "/new-place" (null = pas de redirection)
    String root;                                   // dossier/fichier sur le disque
    String index;                                  // fichier par défaut d'un dossier
    Map<String, String> cgi = new LinkedHashMap<>(); // extension -> interpréteur (".py" -> "python")
    boolean directoryListing = false;              // afficher le contenu d'un dossier ?
}