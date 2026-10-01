import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigLoader {

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "DELETE");

    public static List<ServerConfig> load(String path) throws IOException {

        String text = Files.readString(Path.of(path));
        Object root = JsonParser.parse(text);

        Map<String, Object> rootMap = asMap(root, "root");
        List<Object> serversJson = getList(rootMap, "servers");

        if (serversJson == null || serversJson.isEmpty()) {
            throw new IllegalArgumentException("'servers' must be a non-empty array");
        }

        List<ServerConfig> servers = new ArrayList<>();
        for (int i = 0; i < serversJson.size(); i++) {
            Map<String, Object> m = asMap(serversJson.get(i), "servers[" + i + "]");
            servers.add(parseServer(m, i));
        }

        checkDefaults(servers);
        return servers;
    }

    // ---------- server ----------

    private static ServerConfig parseServer(Map<String, Object> m, int index) {
        String where = "servers[" + index + "]";
        ServerConfig s = new ServerConfig();

        s.host = getString(m, "host", "0.0.0.0");
        s.serverName = getString(m, "serverName", "");
        s.isDefault = getBool(m, "default", false);
        s.clientMaxBodySize = getLong(m, "clientMaxBodySize", 1024 * 1024);

        if (s.clientMaxBodySize <= 0) {
            throw new IllegalArgumentException(where + ".clientMaxBodySize must be > 0");
        }

        // ports
        List<Object> ports = getList(m, "ports");
        if (ports == null || ports.isEmpty()) {
            throw new IllegalArgumentException(where + ".ports must be a non-empty array");
        }
        for (Object p : ports) {
            if (!(p instanceof Long)) {
                throw new IllegalArgumentException(where + ".ports must contain integers");
            }
            long port = (Long) p;
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(where + ": invalid port " + port);
            }
            s.ports.add((int) port);
        }

        // error pages: { "404": "error_pages/404.html" }
        Map<String, Object> errors = getMap(m, "errorPages");
        if (errors != null) {
            for (Map.Entry<String, Object> e : errors.entrySet()) {
                int code;
                try {
                    code = Integer.parseInt(e.getKey());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(where + ".errorPages: bad code '" + e.getKey() + "'");
                }
                if (!(e.getValue() instanceof String)) {
                    throw new IllegalArgumentException(where + ".errorPages." + code + " must be a string");
                }
                s.errorPages.put(code, (String) e.getValue());
            }
        }

        // routes
        List<Object> routes = getList(m, "routes");
        if (routes != null) {
            for (int i = 0; i < routes.size(); i++) {
                String rw = where + ".routes[" + i + "]";
                s.routes.add(parseRoute(asMap(routes.get(i), rw), rw));
            }
        }

        return s;
    }

    // ---------- route ----------

    private static RouteConfig parseRoute(Map<String, Object> m, String where) {
        RouteConfig r = new RouteConfig();

        r.path = getString(m, "path", null);
        if (r.path == null || !r.path.startsWith("/")) {
            throw new IllegalArgumentException(where + ".path is required and must start with '/'");
        }

        List<Object> methods = getList(m, "methods");
        if (methods == null) {
            r.methods.add("GET");                  // défaut
        } else {
            for (Object o : methods) {
                if (!(o instanceof String) || !ALLOWED_METHODS.contains(o)) {
                    throw new IllegalArgumentException(where + ".methods: only GET, POST, DELETE allowed");
                }
                r.methods.add((String) o);
            }
        }

        r.redirect = getString(m, "redirect", null);
        r.root = getString(m, "root", null);
        r.index = getString(m, "index", null);
        r.directoryListing = getBool(m, "directoryListing", false);

        if (r.redirect == null && r.root == null) {
            throw new IllegalArgumentException(where + " needs either 'root' or 'redirect'");
        }

        Map<String, Object> cgi = getMap(m, "cgi");
        if (cgi != null) {
            for (Map.Entry<String, Object> e : cgi.entrySet()) {
                if (!e.getKey().startsWith(".") || !(e.getValue() instanceof String)) {
                    throw new IllegalArgumentException(where + ".cgi must look like { \".py\": \"python\" }");
                }
                r.cgi.put(e.getKey(), (String) e.getValue());
            }
        }

        return r;
    }

    // ---------- validation globale ----------

    // Max 1 serveur "default" par port
    private static void checkDefaults(List<ServerConfig> servers) {
        Map<Integer, Integer> defaultsPerPort = new HashMap<>();
        for (ServerConfig s : servers) {
            if (!s.isDefault) continue;
            for (int port : s.ports) {
                int count = defaultsPerPort.merge(port, 1, Integer::sum);
                if (count > 1) {
                    throw new IllegalArgumentException("Port " + port + " has more than one default server");
                }
            }
        }
    }

    // ---------- helpers de lecture ----------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o, String where) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(where + " must be a JSON object");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o == null) return null;
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("'" + key + "' must be a JSON object");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o == null) return null;
        if (!(o instanceof List)) {
            throw new IllegalArgumentException("'" + key + "' must be an array");
        }
        return (List<Object>) o;
    }

    private static String getString(Map<String, Object> m, String key, String def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof String)) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return (String) o;
    }

    private static boolean getBool(Map<String, Object> m, String key, boolean def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof Boolean)) {
            throw new IllegalArgumentException("'" + key + "' must be true or false");
        }
        return (Boolean) o;
    }

    private static long getLong(Map<String, Object> m, String key, long def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (!(o instanceof Long)) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
        return (Long) o;
    }
}
