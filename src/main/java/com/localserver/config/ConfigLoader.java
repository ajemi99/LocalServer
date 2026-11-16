package com.localserver.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.localserver.constants.Constants;

/**
 * Parses and validates the JSON configuration. This class only produces
 * configuration objects; it never opens sockets or starts the runtime.
 */
public class ConfigLoader {

    /**
     * Loads a configuration file. Relative paths inside the file are resolved
     * against the optional root-level {@code baseDir}, which itself is resolved
     * against the directory containing the configuration file.
     */
    public static List<ServerConfig> load(String path) throws IOException {
        return load(Path.of(path));
    }

    public static List<ServerConfig> load(Path path) throws IOException {
        Path configPath = path.toAbsolutePath().normalize();
        String text = Files.readString(configPath);
        Object root = JsonParser.parse(text);

        Map<String, Object> rootMap = asMap(root, "root");
        Path configDir = configPath.getParent() != null
                ? configPath.getParent()
                : Path.of("").toAbsolutePath().normalize();
        Path baseDir = resolveBaseDir(rootMap, configDir);

        List<Object> serversJson = getList(rootMap, Constants.Config.KEY_SERVERS);

        if (serversJson == null || serversJson.isEmpty()) {
            throw new IllegalArgumentException("'servers' must be a non-empty array");
        }

        List<ServerConfig> servers = new ArrayList<>();
        for (int i = 0; i < serversJson.size(); i++) {
            Map<String, Object> m = asMap(serversJson.get(i), "servers[" + i + "]");
            servers.add(parseServer(m, i, baseDir));
        }

        checkDefaults(servers);
        return servers;
    }

    private static Path resolveBaseDir(Map<String, Object> rootMap, Path configDir) {
        String configured = getString(rootMap, Constants.Config.KEY_BASE_DIR, null);
        if (configured == null) {
            return configDir;
        }

        Path base = Path.of(configured);
        Path resolved = base.isAbsolute() ? base.normalize() : configDir.resolve(base).normalize();
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("'" + Constants.Config.KEY_BASE_DIR + "' is not a directory: " + resolved);
        }
        return resolved;
    }

    // ---------- server ----------

    private static ServerConfig parseServer(Map<String, Object> m, int index, Path baseDir) {
        String where = "servers[" + index + "]";
        ServerConfig s = new ServerConfig();
        s.baseDir = baseDir;

        s.host = getString(m, Constants.Config.KEY_HOST, Constants.Server.DEFAULT_HOST);
        s.serverName = getString(m, Constants.Config.KEY_SERVER_NAME, "");
        s.isDefault = getBool(m, Constants.Config.KEY_DEFAULT, false);
        s.clientMaxBodySize = getLong(m, Constants.Config.KEY_CLIENT_MAX_BODY_SIZE, Constants.Server.DEFAULT_CLIENT_MAX_BODY_SIZE);

        if (s.clientMaxBodySize <= 0) {
            throw new IllegalArgumentException(where + ".clientMaxBodySize must be > 0");
        }

        // ports
        List<Object> ports = getList(m, Constants.Config.KEY_PORTS);
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
        Map<String, Object> errors = getMap(m, Constants.Config.KEY_ERROR_PAGES);
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
        List<Object> routes = getList(m, Constants.Config.KEY_ROUTES);
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

        r.path = getString(m, Constants.Config.KEY_PATH, null);
        if (r.path == null || !r.path.startsWith("/")) {
            throw new IllegalArgumentException(where + ".path is required and must start with '/'");
        }

        List<Object> methods = getList(m, Constants.Config.KEY_METHODS);
        if (methods == null) {
            r.methods.add(Constants.Config.DEFAULT_METHOD);
        } else {
            for (Object o : methods) {
                if (!(o instanceof String) || !Constants.Config.ALLOWED_METHODS.contains(o)) {
                    throw new IllegalArgumentException(where + ".methods: only GET, POST, DELETE allowed");
                }
                r.methods.add((String) o);
            }
        }

        r.redirect = getString(m, Constants.Config.KEY_REDIRECT, null);
        r.root = getString(m, Constants.Config.KEY_ROOT, null);
        r.index = getString(m, Constants.Config.KEY_INDEX, null);
        r.directoryListing = getBool(m, Constants.Config.KEY_DIRECTORY_LISTING, false);

        if (r.redirect == null && r.root == null) {
            throw new IllegalArgumentException(where + " needs either 'root' or 'redirect'");
        }

        Map<String, Object> cgi = getMap(m, Constants.Config.KEY_CGI);
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

    // ---------- global validation ----------

    // Max 1 "default" server per port
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

    // ---------- read helpers ----------

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
