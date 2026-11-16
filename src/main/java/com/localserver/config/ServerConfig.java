package com.localserver.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.localserver.constants.Constants;

/**
 * Configuration of one virtual server.
 *
 * <p>Paths read from the configuration file stay relative until they are resolved
 * through {@link #resolvePath(String)}. They are resolved against {@link #baseDir},
 * which is the directory selected by the configuration file (usually the project
 * root or the installation data directory). This keeps the server independent of
 * the process working directory.</p>
 */
public class ServerConfig {

    public String host = Constants.Server.DEFAULT_HOST;
    public List<Integer> ports = new ArrayList<>();
    public String serverName = "";                        // used with the Host header
    public boolean isDefault = false;                     // default server for its ports
    public long clientMaxBodySize = Constants.Server.DEFAULT_CLIENT_MAX_BODY_SIZE; // bytes (default 1 MiB)
    public Map<Integer, String> errorPages = new LinkedHashMap<>(); // 404 -> "error_pages/404.html"
    public List<RouteConfig> routes = new ArrayList<>();

    /** Directory used to resolve every relative path from this configuration. */
    public Path baseDir = Path.of("").toAbsolutePath().normalize();

    /**
     * Resolves a configured path against {@link #baseDir}. Absolute paths are kept
     * as they are; relative paths never depend on the JVM working directory.
     */
    public Path resolvePath(String path) {
        Path configured = Path.of(path);
        return configured.isAbsolute()
                ? configured.normalize()
                : baseDir.resolve(configured).normalize();
    }
}
