package com.localserver.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.localserver.constants.Constants;
import com.localserver.logging.LoggingDebuger;
import com.localserver.routing.Route;

/**
 * Parses and validates the JSON configuration. This class only produces
 * configuration objects; it never opens sockets or starts the runtime.
 *
 * <p>
 * The legacy {@code host} and {@code serverName} fields are still accepted for
 * backward compatibility. {@code bindAddress} and {@code serverNames} take
 * precedence, and specifying both an old and a new field at the same time is
 * rejected so the configuration cannot be ambiguous.
 * </p>
 */
public class ConfigLoader {

    /** A listening endpoint: a bind address and a port. */
    private record Endpoint(String address, int port) {
    }

    public static List<ServerConfig> load(String path) throws IOException {
        return load(Path.of(path));
    }

    public static List<ServerConfig> load(Path path) throws IOException {
        Path configPath = path.toAbsolutePath().normalize();
        String text = Files.readString(configPath);

        Object root = JsonParser.parse(text);

        Map<String, Object> rootMap = asMap(root, "root");

        // Debug
        LoggingDebuger.printPretty(rootMap);
        // Debug

        Path configDir = configPath.getParent() != null
                ? configPath.getParent()
                : Path.of("").toAbsolutePath().normalize();
        Path baseDir = resolveBaseDir(rootMap, configDir);

        // Debug
        LoggingDebuger.printPretty("configDir:" + configDir + " baseDir:" + baseDir);
        // Debug

        List<Object> serversJson = getList(rootMap, Constants.Config.KEY_SERVERS, "root");

        if (serversJson == null || serversJson.isEmpty()) {
            throw new IllegalArgumentException("'servers' attribute is missing or 'servers' array is empty.");
        }

        List<ServerConfig> servers = new ArrayList<>();
        for (int i = 0; i < serversJson.size(); i++) {
            Map<String, Object> m = asMap(serversJson.get(i), "servers[" + i + "]");
            servers.add(parseServer(m, i, baseDir));
        }

        checkEndpoints(servers);
        return servers;
    }

    private static Path resolveBaseDir(Map<String, Object> rootMap, Path configDir) {
        String configured = getString(rootMap, Constants.Config.KEY_BASE_DIR, "root", null);
        if (configured == null) {
            return configDir;
        }

        Path base = Path.of(configured);
        Path resolved = base.isAbsolute() ? base.normalize() : configDir.resolve(base).normalize();
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException(
                    "'" + Constants.Config.KEY_BASE_DIR + "' is not a directory: " + resolved);
        }
        return resolved;
    }

    // ---------- server ----------

    private static ServerConfig parseServer(Map<String, Object> m, int index, Path baseDir) {
        String where = "servers[" + index + "]";
        ServerConfig s = new ServerConfig();
        s.baseDir = baseDir;

        s.bindAddress = parseBindAddress(m, where);
        s.serverNames = parseServerNames(m, where);
        s.isDefault = getBool(m, Constants.Config.KEY_DEFAULT, false, where);
        s.clientMaxBodySize = getLong(m, Constants.Config.KEY_CLIENT_MAX_BODY_SIZE, where,
                Constants.Server.DEFAULT_CLIENT_MAX_BODY_SIZE);

        if (s.clientMaxBodySize <= 0) {
            throw new IllegalArgumentException(where + ".clientMaxBodySize must be > 0");
        }

        s.ports = parsePorts(m, where);
        s.errorPages = parseErrorPages(m, where);
        s.routes = parseRoutes(m, where);

        return s;
    }

    private static String parseBindAddress(Map<String, Object> m, String where) {
        boolean hasNew = m.containsKey(Constants.Config.KEY_BIND_ADDRESS);
        boolean hasLegacy = m.containsKey(Constants.Config.KEY_HOST);

        if (hasNew && hasLegacy) {
            throw new IllegalArgumentException(
                    where + " must not define both 'bindAddress' and the legacy 'host' field");
        }
        if (!hasNew && !hasLegacy) {
            return Constants.Server.DEFAULT_HOST;
        }

        String key = hasNew ? Constants.Config.KEY_BIND_ADDRESS : Constants.Config.KEY_HOST;
        String value = getString(m, key, where, null);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + key + " must be a string");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(where + "." + key + " must not be empty");
        }
        return value;
    }

    private static List<String> parseServerNames(Map<String, Object> m, String where) {
        boolean hasNew = m.containsKey(Constants.Config.KEY_SERVER_NAMES);
        boolean hasLegacy = m.containsKey(Constants.Config.KEY_SERVER_NAME);

        if (hasNew && hasLegacy) {
            throw new IllegalArgumentException(
                    where + " must not define both 'serverNames' and the legacy 'serverName' field");
        }

        List<String> names = new ArrayList<>();

        if (hasNew) {
            List<Object> entries = getList(m, Constants.Config.KEY_SERVER_NAMES, where);
            if (entries == null) {
                return names;
            }

            Set<String> seen = new HashSet<>();
            for (int i = 0; i < entries.size(); i++) {
                Object entry = entries.get(i);
                String entryWhere = where + "." + Constants.Config.KEY_SERVER_NAMES + "[" + i + "]";
                if (!(entry instanceof String)) {
                    throw new IllegalArgumentException(entryWhere + " must be a string");
                }
                String name = (String) entry;
                if (name.isBlank()) {
                    throw new IllegalArgumentException(entryWhere + " must not be empty");
                }

                String normalized = name.toLowerCase(Locale.ROOT);
                if (!seen.add(normalized)) {
                    throw new IllegalArgumentException(
                            where + "." + Constants.Config.KEY_SERVER_NAMES
                                    + " must not contain duplicate name '" + name + "'");
                }
                names.add(normalized);
            }
        } else if (hasLegacy) {
            String name = getString(m, Constants.Config.KEY_SERVER_NAME, where, "");
            if (name == null) {
                throw new IllegalArgumentException(where + ".serverName must be a string");
            }
            // The legacy field used "" to mean "no names".
            if (!name.isBlank()) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }

        return names;
    }

    private static List<Integer> parsePorts(Map<String, Object> m, String where) {
        List<Object> entries = getList(m, Constants.Config.KEY_PORTS, where);
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(where + ".ports must be a non-empty array");
        }

        List<Integer> ports = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            String entryWhere = where + ".ports[" + i + "]";
            if (!(entry instanceof Long)) {
                throw new IllegalArgumentException(entryWhere + " must be an integer");
            }

            long port = (Long) entry;
            if (port < Constants.Server.DEFAULT_PORT_MIN || port > Constants.Server.DEFAULT_PORT_MAX) {
                throw new IllegalArgumentException(entryWhere + " must be between "
                        + Constants.Server.DEFAULT_PORT_MIN + " and " + Constants.Server.DEFAULT_PORT_MAX);
            }

            int value = (int) port;
            if (!seen.add(value)) {
                throw new IllegalArgumentException(
                        where + ".ports must not contain duplicate port " + value);
            }
            ports.add(value);
        }
        return ports;
    }

    private static Map<Integer, String> parseErrorPages(Map<String, Object> m, String where) {
        Map<Integer, String> errorPages = new LinkedHashMap<>();
        Map<String, Object> entries = getMap(m, Constants.Config.KEY_ERROR_PAGES, where);
        if (entries == null) {
            return errorPages;
        }

        for (Map.Entry<String, Object> e : entries.entrySet()) {
            String entryWhere = where + "." + Constants.Config.KEY_ERROR_PAGES + "." + e.getKey();

            int code;
            try {
                code = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        entryWhere + " must be an integer error status code", ex);
            }

            if (!Constants.Status.SUPPORTED_ERROR_CODES.contains(code)) {
                throw new IllegalArgumentException(entryWhere + ": unsupported error status code");
            }

            if (!(e.getValue() instanceof String)) {
                throw new IllegalArgumentException(entryWhere + " must be a string");
            }

            String file = (String) e.getValue();
            if (file.isEmpty()) {
                throw new IllegalArgumentException(entryWhere + " must not be empty");
            }

            errorPages.put(code, file);
        }
        return errorPages;
    }

    private static List<RouteConfig> parseRoutes(Map<String, Object> m, String where) {
        List<RouteConfig> routes = new ArrayList<>();
        List<Object> entries = getList(m, Constants.Config.KEY_ROUTES, where);
        if (entries == null) {
            return routes;
        }

        // The router picks a single route by longest matching prefix and keeps
        // the first one on ties, so two routes with the same prefix would make
        // the second unreachable. Reject them instead of silently ignoring one.
        Set<String> prefixes = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String routeWhere = where + ".routes[" + i + "]";
            RouteConfig route = parseRoute(asMap(entries.get(i), routeWhere), routeWhere);

            if (!prefixes.add(Route.prefix(route.path))) {
                throw new IllegalArgumentException(
                        routeWhere + ".path duplicates another route path '" + route.path + "'");
            }
            routes.add(route);
        }
        return routes;
    }

    // ---------- route ----------

    private static RouteConfig parseRoute(Map<String, Object> m, String where) {
        RouteConfig r = new RouteConfig();

        r.path = getString(m, Constants.Config.KEY_PATH, where, null);
        validatePath(r.path, where);

        r.methods = parseMethods(m, where);

        Map<String, Object> redirect = getMap(m, Constants.Config.KEY_REDIRECT, where);
        String root = getString(m, Constants.Config.KEY_ROOT, where, null);

        if (root != null && root.isEmpty()) {
            throw new IllegalArgumentException(where + ".root must not be empty");
        }

        if (redirect != null && root != null) {
            throw new IllegalArgumentException(
                    where + " must define either 'root' or 'redirect', not both");
        }
        if (redirect == null && root == null) {
            throw new IllegalArgumentException(where + " must define either 'root' or 'redirect'");
        }

        if (redirect != null) {
            r.redirect = parseRedirect(redirect, where);
        } else {
            r.root = root;
        }

        r.index = getString(m, Constants.Config.KEY_INDEX, where, null);
        if (r.index != null && r.index.isEmpty()) {
            throw new IllegalArgumentException(where + ".index must not be empty");
        }

        r.directoryListing = getBool(m, Constants.Config.KEY_DIRECTORY_LISTING, false, where);

        r.cgi = parseCgi(m, where);
        if (!r.cgi.isEmpty() && r.root == null) {
            throw new IllegalArgumentException(where + ".cgi requires 'root'");
        }

        return r;
    }

    private static void validatePath(String path, String where) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(where + ".path is required and must not be empty");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(where + ".path must start with '/'");
        }
        if (path.indexOf('\0') != -1) {
            throw new IllegalArgumentException(where + ".path must not contain a null byte");
        }
        if (path.indexOf('?') != -1 || path.indexOf('#') != -1) {
            throw new IllegalArgumentException(where + ".path must not contain '?' or '#'");
        }
        if (path.indexOf('\\') != -1) {
            throw new IllegalArgumentException(where + ".path must not contain '\\\\'");
        }
        if (path.length() > 1 && path.contains("//")) {
            throw new IllegalArgumentException(where + ".path must not contain '//'");
        }

        for (String segment : path.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        where + ".path must not contain '.' or '..' segments");
            }
        }
    }

    private static List<String> parseMethods(Map<String, Object> m, String where) {
        List<Object> entries = getList(m, Constants.Config.KEY_METHODS, where);
        if (entries == null) {
            List<String> defaults = new ArrayList<>();
            defaults.add(Constants.Config.DEFAULT_METHOD);
            return defaults;
        }

        List<String> methods = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            String entryWhere = where + ".methods[" + i + "]";
            if (!(entry instanceof String)) {
                throw new IllegalArgumentException(entryWhere + " must be a string");
            }

            String method = (String) entry;
            if (!Constants.Config.ALLOWED_METHODS.contains(method)) {
                throw new IllegalArgumentException(
                        entryWhere + " must be one of GET, POST, DELETE");
            }
            if (!seen.add(method)) {
                throw new IllegalArgumentException(
                        where + ".methods must not contain duplicate method '" + method + "'");
            }
            methods.add(method);
        }

        if (methods.isEmpty()) {
            throw new IllegalArgumentException(where + ".methods must not be empty");
        }
        return methods;
    }

    private static RedirectConfig parseRedirect(Map<String, Object> redirect, String where) {
        String redirectWhere = where + "." + Constants.Config.KEY_REDIRECT;

        String url = getString(redirect, Constants.Config.KEY_URL, redirectWhere, null);
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException(redirectWhere + ".url is required and must not be empty");
        }

        // Validate the original numeric value before narrowing it to int.
        long status = getLong(redirect, Constants.Config.KEY_STATUS, redirectWhere,
                Constants.Config.DEFAULT_REDIRECT_STATUS);
        if (!Constants.Status.REDIRECT_CODES.contains(status)) {
            throw new IllegalArgumentException(
                    redirectWhere + ".status must be one of 301, 302, 303, 307, 308");
        }

        return new RedirectConfig(url, (int) status);
    }

    private static Map<String, String> parseCgi(Map<String, Object> m, String where) {
        Map<String, String> cgi = new LinkedHashMap<>();
        Map<String, Object> entries = getMap(m, Constants.Config.KEY_CGI, where);
        if (entries == null) {
            return cgi;
        }

        String cgiWhere = where + "." + Constants.Config.KEY_CGI;
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            String extension = e.getKey();
            if (extension.isEmpty()) {
                throw new IllegalArgumentException(cgiWhere + ": extension must not be empty");
            }
            if (!extension.startsWith(".")) {
                throw new IllegalArgumentException(cgiWhere + ": extension '" + extension + "' must start with '.'");
            }
            if (extension.length() == 1) {
                throw new IllegalArgumentException(cgiWhere + ": extension must not be empty");
            }

            Object value = e.getValue();
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(cgiWhere + "['" + extension + "'] must be a string");
            }

            String interpreter = (String) value;
            if (interpreter.trim().isEmpty()) {
                throw new IllegalArgumentException(cgiWhere + "['" + extension + "'] must not be empty");
            }

            cgi.put(extension, interpreter);
        }
        return cgi;
    }

    // ---------- global validation ----------

    /**
     * Validates listening endpoints (bind address + port). A single server can
     * listen on several ports, so every endpoint is validated independently.
     * Virtual servers may share an endpoint, but an endpoint with more than one
     * virtual server needs exactly one default server.
     */
    private static void checkEndpoints(List<ServerConfig> servers) {
        Map<Endpoint, List<ServerConfig>> byEndpoint = new LinkedHashMap<>();
        for (ServerConfig s : servers) {
            for (int port : s.ports) {
                Endpoint endpoint = new Endpoint(s.bindAddress, port);
                byEndpoint.computeIfAbsent(endpoint, key -> new ArrayList<>()).add(s);
            }
        }

        for (Map.Entry<Endpoint, List<ServerConfig>> e : byEndpoint.entrySet()) {
            Endpoint endpoint = e.getKey();
            List<ServerConfig> group = e.getValue();
            String where = "endpoint " + endpoint.address() + ":" + endpoint.port();

            Map<String, ServerConfig> nameOwner = new HashMap<>();
            for (ServerConfig s : group) {
                for (String name : s.serverNames) {
                    ServerConfig previous = nameOwner.putIfAbsent(name, s);
                    if (previous != null && previous != s) {
                        throw new IllegalArgumentException(
                                where + " has duplicate server name '" + name + "'");
                    }
                }
            }

            long defaults = group.stream().filter(s -> s.isDefault).count();
            if (defaults > 1) {
                throw new IllegalArgumentException(where + " has more than one default server");
            }
            if (group.size() > 1 && defaults != 1) {
                throw new IllegalArgumentException(
                        where + " has multiple virtual servers and requires exactly one default server");
            }
        }
    }

    // ---------- read helpers ----------

    private static Map<String, Object> asMap(Object o, String where) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(where + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) o;
        return map;
    }

    private static Map<String, Object> getMap(Map<String, Object> m, String key, String where) {
        Object o = m.get(key);
        if (o == null) {
            return null;
        }
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(where + "." + key + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) o;
        return map;
    }

    private static List<Object> getList(Map<String, Object> m, String key, String where) {
        Object o = m.get(key);
        if (o == null) {
            return null;
        }
        if (!(o instanceof List)) {
            throw new IllegalArgumentException(where + "." + key + " must be an array");
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) o;
        return list;
    }

    private static String getString(Map<String, Object> m, String key, String where, String def) {
        Object o = m.get(key);
        if (o == null) {
            return def;
        }
        if (!(o instanceof String)) {
            throw new IllegalArgumentException(where + "." + key + " must be a string");
        }
        return (String) o;
    }

    private static boolean getBool(Map<String, Object> m, String key, boolean def, String where) {
        Object o = m.get(key);
        if (o == null) {
            return def;
        }
        if (!(o instanceof Boolean)) {
            throw new IllegalArgumentException(where + "." + key + " must be true or false");
        }
        return (Boolean) o;
    }

    private static long getLong(Map<String, Object> m, String key, String where, long def) {
        Object o = m.get(key);
        if (o == null) {
            return def;
        }
        if (!(o instanceof Long)) {
            throw new IllegalArgumentException(where + "." + key + " must be an integer");
        }
        return (Long) o;
    }
}