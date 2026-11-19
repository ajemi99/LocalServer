package com.localserver.server;

import com.localserver.config.ServerConfig;
import com.localserver.logging.LoggingManger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the listening sockets, the selector and the event loop. It contains only
 * networking concerns; configuration loading and process lifecycle live in the
 * runtime package.
 */
public final class Server {

    private final List<ServerConfig> servers;
    private final List<ServerSocketChannel> listeningChannels = new ArrayList<>();
    private final Map<Endpoint, List<ServerConfig>> endpointsMap = new LinkedHashMap<>();

    private Selector selector;
    private EventLoop eventLoop;
    private boolean started;
    private boolean running;
    private boolean closed;

    public Server(List<ServerConfig> servers) {
        this.servers = List.copyOf(servers);
    }

    /**
     * Binds every configured host/port, then blocks in the event loop until
     * {@link #stop()} is called. Returns {@code false} when no port could be
     * opened.
     */
    public boolean start() throws IOException {
        if (started) {
            throw new IllegalStateException("Server has already been started");
        }
        started = true;

        selector = Selector.open();
        for (ServerConfig sc : servers) {
            for (int port : sc.ports) {
                Endpoint epoint = new Endpoint(sc.bindAddress, port);

                endpointsMap.computeIfAbsent(epoint, k -> new ArrayList<>()).add(sc);
            }
        }

        // One ServerSocketChannel per port
        for (Endpoint epoint : endpointsMap.keySet()) {
            ServerSocketChannel server = null;
            try {
                server = ServerSocketChannel.open();
                server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                server.bind(new InetSocketAddress(epoint.address(), epoint.port()));
                server.configureBlocking(false);
                server.register(selector, SelectionKey.OP_ACCEPT, endpointsMap.get(epoint));
                listeningChannels.add(server);
                LoggingManger.info("Listening on " + epoint.address() + ":" + epoint.port());
            } catch (IOException ex) {
                LoggingManger.severe("Cannot bind port " + epoint.port() + ": " + ex.getMessage(), ex);
                if (server != null) {
                    try {
                        server.close();
                    } catch (IOException closeError) {
                        LoggingManger.severe(
                                "Cannot close " + epoint.address() + ":" + epoint.port() + ": "
                                        + closeError.getMessage(),
                                closeError);
                    }
                }
            }
        }

        if (selector.keys().isEmpty()) {
            LoggingManger.severe("No port could be opened. Exiting.");
            closeAll();
            return false;
        }

        eventLoop = new EventLoop(selector, servers);
        running = true;
        try {
            eventLoop.run();
        } finally {
            running = false;
            closeAll();
        }
        return true;
    }

    /**
     * Signals the event loop to stop and closes listening/client channels and
     * the selector. Safe to call more than once and from a shutdown hook.
     */
    public void stop() {
        running = false;

        EventLoop loop = eventLoop;
        if (loop != null) {
            loop.stop();
        }

        Selector current = selector;
        if (current != null) {
            current.wakeup();
        }

        // closeAll();
    }

    private void closeAll() {
        if (closed) {
            return;
        }
        closed = true;

        for (ServerSocketChannel channel : listeningChannels) {
            try {
                channel.close();
            } catch (IOException e) {
                LoggingManger.severe("Cannot close listening channel: " + e.getMessage(), e);
            }
        }
        listeningChannels.clear();

        Selector current = selector;
        if (current == null) {
            return;
        }

        try {
            for (SelectionKey key : current.keys()) {
                try {
                    key.channel().close();
                } catch (IOException e) {
                    LoggingManger.severe("Cannot close channel: " + e.getMessage(), e);
                }
                key.cancel();
            }
        } catch (ClosedSelectorException e) {
            // Already closed: nothing left to clean.
        }

        try {
            current.close();
        } catch (IOException e) {
            LoggingManger.severe("Cannot close selector: " + e.getMessage(), e);
        }
    }

    public boolean isRunning() {
        return running;
    }
}
