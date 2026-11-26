package com.localserver.server;

import com.localserver.config.ServerConfig;
import com.localserver.http.session.SessionManager;
import com.localserver.logging.LoggingManger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

import com.localserver.constants.Constants;

public final class EventLoop {

    private final Selector selector;
    private final List<ServerConfig> servers;
    private final SessionManager sessionManager;
    private final HttpRequestProcessor requestProcessor;
    private boolean running = true;

    public EventLoop(Selector selector, List<ServerConfig> servers) {
        this.selector = selector;
        this.servers = servers;
        this.sessionManager = new SessionManager();
        this.requestProcessor = new HttpRequestProcessor(sessionManager);
    }

    public void stop() {
        running = false;
        sessionManager.close();
    }

    private void acceptClient(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            SocketChannel client = serverChannel.accept();
            if (client == null) {
                return;
            }
            client.configureBlocking(false);

            Connection connection = new Connection();
            connection.localPort = ((InetSocketAddress) client.getLocalAddress()).getPort();
            client.register(selector, SelectionKey.OP_READ, connection);

            LoggingManger.info("Client connected on port " + connection.localPort
                    + ": " + client.getRemoteAddress());
        }
    }

    private void readClient(SelectionKey key) throws IOException {
        if (key.isReadable()) {
            requestProcessor.process(key, servers);
        }
    }

    private void writeClient(SelectionKey key) throws IOException {
        if (key.isWritable()) {
            SocketChannel client = (SocketChannel) key.channel();
            Connection connection = (Connection) key.attachment();
            ByteBuffer response = connection.responseBuffer;

            client.write(response);

            if (!response.hasRemaining()) {
                if (connection.closeAfterWrite) {
                    closeClient(key, client);
                } else {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
        }
    }

    private void closeClient(SelectionKey key, SocketChannel client) throws IOException {
        client.close();
        key.cancel();
    }

    public void run() {
        while (running) {
            try {
                selector.select(Constants.Server.SELECTOR_TIMEOUT_MS);
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    try {
                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            acceptClient(key);
                        } else if (key.isReadable()) {
                            readClient(key);
                        } else if (key.isWritable()) {
                            writeClient(key);
                        }

                    } catch (Exception e) {
                        LoggingManger.warning("Client error: " + e.getMessage(), e);
                        try {
                            key.channel().close();
                        } catch (IOException closeError) {
                            LoggingManger.warning(
                                    "Cannot close client channel: " + closeError.getMessage(), closeError);
                        }
                        key.cancel();
                    }
                }

            } catch (ClosedSelectorException e) {
                if (running) {
                    LoggingManger.warning("Loop error: " + e, e);
                }
            } catch (Exception e) {
                LoggingManger.severe("Loop error: " + e.getMessage(), e);
            }
        }
    }
}
