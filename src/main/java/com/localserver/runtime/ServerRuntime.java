package com.localserver.runtime;

import com.localserver.config.ConfigLoader;
import com.localserver.config.ServerConfig;
import com.localserver.server.Server;

import java.io.IOException;
import java.util.List;

/**
 * Application lifecycle: loads and validates configuration, creates the server
 * and coordinates shutdown. No HTTP or socket logic belongs here.
 */
public final class ServerRuntime {

    private final String configPath;
    private final List<ServerConfig> servers;
    private volatile Server server;

    public ServerRuntime(String configPath) throws IOException {
        this.configPath = configPath;
        this.servers = ConfigLoader.load(configPath);
    }

    public String configPath() {
        return configPath;
    }

    /**
     * Starts the server and blocks until the event loop stops.
     */
    public void start() throws IOException {
        Server current = new Server(servers);
        server = current;
        try {
            current.start();
        } finally {
            server = null;
        }
    }

    /**
     * Requests shutdown of the running server. Safe to call from a JVM shutdown
     * hook; it performs no process/daemon management itself.
     */
    public void stop() {
        Server current = server;
        if (current != null) {
            current.stop();
        }
    }
}
