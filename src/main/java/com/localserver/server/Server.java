package com.localserver.server;

import com.localserver.config.ServerConfig;

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

    private Selector selector;
    private EventLoop eventLoop;
    private volatile boolean running;
    private boolean closed;

    public Server(List<ServerConfig> servers) {
        this.servers = servers;
    }

    /**
     * Binds every configured host/port, then blocks in the event loop until
     * {@link #stop()} is called. Returns {@code false} when no port could be
     * opened.
     */
    public boolean start() throws IOException {
        selector = Selector.open();

        // port -> host (one channel per port)
        Map<Integer, String> portToHost = new LinkedHashMap<>();
        for (ServerConfig sc : servers) {
            for (int port : sc.ports) {
                portToHost.putIfAbsent(port, sc.host);
            }
        }

        // One ServerSocketChannel per port
        for (Map.Entry<Integer, String> e : portToHost.entrySet()) {
            ServerSocketChannel server = null;
            try {
                server = ServerSocketChannel.open();
                server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                server.bind(new InetSocketAddress(e.getValue(), e.getKey()));
                server.configureBlocking(false);
                server.register(selector, SelectionKey.OP_ACCEPT);
                listeningChannels.add(server);
                System.out.println("Listening on " + e.getValue() + ":" + e.getKey());
            } catch (IOException ex) {
                System.err.println("Cannot bind port " + e.getKey() + ": " + ex.getMessage());
                if (server != null) {
                    try {
                        server.close();
                    } catch (IOException closeError) {
                        System.err.println("Cannot close port " + e.getKey() + ": " + closeError.getMessage());
                    }
                }
            }
        }

        if (selector.keys().isEmpty()) {
            System.err.println("No port could be opened. Exiting.");
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

        closeAll();
    }

    public boolean isRunning() {
        return running;
    }

    private synchronized void closeAll() {
        if (closed) {
            return;
        }
        closed = true;

        for (ServerSocketChannel channel : listeningChannels) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("Cannot close listening channel: " + e.getMessage());
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
                    System.err.println("Cannot close channel: " + e.getMessage());
                }
                key.cancel();
            }
        } catch (ClosedSelectorException e) {
            // Already closed: nothing left to clean.
        }

        try {
            current.close();
        } catch (IOException e) {
            System.err.println("Cannot close selector: " + e.getMessage());
        }
    }
}
