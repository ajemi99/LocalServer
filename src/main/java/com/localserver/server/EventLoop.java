package com.localserver.server;

import com.localserver.config.ServerConfig;
import com.localserver.http.BadRequestException;
import com.localserver.http.ChunkedDecoder;
import com.localserver.http.ErrorPages;
import com.localserver.http.HttpParser;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;
import com.localserver.routing.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.localserver.constants.Constants;

/**
 * Single-threaded non-blocking event loop. One {@link Selector} handles all
 * listening sockets and client connections, preserving the original
 * single-process/single-thread architecture.
 */
public final class EventLoop {

    private static final int MAX_HEADER_SIZE = Constants.Server.MAX_HEADER_SIZE;

    private final Selector selector;
    private final List<ServerConfig> servers;
    private volatile boolean running = true;

    public EventLoop(Selector selector, List<ServerConfig> servers) {
        this.selector = selector;
        this.servers = servers;
    }

    public void stop() {
        running = false;
    }

    public void run() {
        while (running) {
            try {
                selector.select(Constants.Server.SELECTOR_TIMEOUT_MS);

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {

                    SelectionKey key = iterator.next();
                    iterator.remove();

                    // try/catch per client: one error must not kill the server
                    try {

                        if (!key.isValid()) {
                            continue;
                        }

                        // =========================
                        // NEW CLIENT
                        // =========================
                        if (key.isAcceptable()) {

                            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

                            SocketChannel client = serverChannel.accept();
                            if (client == null) {
                                continue;
                            }

                            client.configureBlocking(false);

                            Connection connection = new Connection();
                            // Which port did this client arrive on? Used to choose the server.
                            connection.localPort =
                                    ((InetSocketAddress) client.getLocalAddress()).getPort();

                            SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
                            clientKey.attach(connection);

                            System.out.println("Client connected on port " + connection.localPort
                                    + ": " + client.getRemoteAddress());
                        }

                        // =========================
                        // CLIENT SENT DATA
                        // =========================
                        else if (key.isReadable()) {

                            SocketChannel client = (SocketChannel) key.channel();
                            Connection connection = (Connection) key.attachment();
                            ByteBuffer buffer = connection.requestBuffer;

                            int bytesRead = client.read(buffer);

                            if (bytesRead == -1) {
                                client.close();
                                key.cancel();
                                continue;
                            }

                            buffer.flip();

                            connection.append(buffer);
                            buffer.clear();

                            if (connection.headerEnd == -1) {
                                connection.headerEnd = HttpParser.findHeaderEnd(connection.data, connection.length);

                                boolean tooLarge = connection.headerEnd == -1
                                        ? connection.length > MAX_HEADER_SIZE
                                        : connection.headerEnd > MAX_HEADER_SIZE;

                                if (tooLarge) {
                                    connection.responseBuffer =
                                            ByteBuffer.wrap(ErrorPages.get(413).build());
                                    connection.closeAfterWrite = true;
                                    key.interestOps(SelectionKey.OP_WRITE);
                                    continue;
                                }

                                if (connection.headerEnd == -1) {
                                    System.out.println("Request not complete yet...");
                                    continue;
                                }
                            }

                            HttpResponse httpResponse;

                            try {
                                if (connection.pending == null) {
                                    byte[] rawHeaders = Arrays.copyOf(connection.data, connection.headerEnd + 4);
                                    connection.pending = HttpParser.parse(rawHeaders);
                                    connection.contentLength = HttpParser.getContentLength(connection.pending);

                                    ServerConfig selected = Router.selectServer(
                                            connection.pending, connection.localPort, servers);
                                    long maxBody = selected != null
                                            ? selected.clientMaxBodySize
                                            : Constants.Server.DEFAULT_CLIENT_MAX_BODY_SIZE;

                                    String transferEncoding =
                                            connection.pending.headers.get(Constants.Http.HEADER_TRANSFER_ENCODING);
                                    if (transferEncoding != null) {
                                        if (!transferEncoding.trim().equalsIgnoreCase("chunked")
                                                || connection.pending.headers.containsKey(Constants.Http.HEADER_CONTENT_LENGTH)) {
                                            throw new BadRequestException("Unsupported Transfer-Encoding");
                                        }
                                        connection.decoder = new ChunkedDecoder(
                                                connection.headerEnd + 4, maxBody);
                                    } else if (connection.contentLength > maxBody) {
                                        connection.responseBuffer = ByteBuffer.wrap(
                                                (selected != null
                                                        ? ErrorPages.get(413, selected)
                                                        : ErrorPages.get(413)).build());
                                        connection.closeAfterWrite = true;
                                        key.interestOps(SelectionKey.OP_WRITE);
                                        continue;
                                    }
                                }

                                int bodyStart = connection.headerEnd + 4;
                                HttpRequest httpRequest = connection.pending;

                                if (connection.decoder != null) {
                                    ChunkedDecoder.Status status =
                                            connection.decoder.feed(connection.data, connection.length);

                                    if (status == ChunkedDecoder.Status.BAD) {
                                        throw new BadRequestException("Bad chunked encoding");
                                    }
                                    if (status == ChunkedDecoder.Status.TOO_LARGE) {
                                        ServerConfig selected = Router.selectServer(
                                                httpRequest, connection.localPort, servers);
                                        connection.responseBuffer = ByteBuffer.wrap(
                                                (selected != null
                                                        ? ErrorPages.get(413, selected)
                                                        : ErrorPages.get(413)).build());
                                        connection.closeAfterWrite = true;
                                        key.interestOps(SelectionKey.OP_WRITE);
                                        continue;
                                    }
                                    if (status == ChunkedDecoder.Status.INCOMPLETE) {
                                        System.out.println("Chunked body not complete yet...");
                                        continue;
                                    }

                                    httpRequest.body = connection.decoder.body();
                                    httpRequest.headers.remove(Constants.Http.HEADER_TRANSFER_ENCODING);
                                    httpRequest.headers.put(Constants.Http.HEADER_CONTENT_LENGTH,
                                            String.valueOf(httpRequest.body.length));
                                } else {
                                    int available = connection.length - bodyStart;
                                    if (available < connection.contentLength) {
                                        System.out.println("Body not complete yet... ("
                                                + available + "/" + connection.contentLength + ")");
                                        continue;
                                    }
                                    httpRequest.body = Arrays.copyOfRange(
                                            connection.data, bodyStart, bodyStart + connection.contentLength);
                                }

                                System.out.println(httpRequest.method + " " + httpRequest.path);

                                httpResponse = Router.route(httpRequest, connection.localPort, servers);

                            } catch (BadRequestException e) {
                                httpResponse = ErrorPages.get(400);
                            } catch (Exception e) {
                                e.printStackTrace();
                                httpResponse = ErrorPages.get(500);
                            }

                            connection.responseBuffer = ByteBuffer.wrap(httpResponse.build());
                            connection.reset();
                            key.interestOps(SelectionKey.OP_WRITE);
                        }

                        // =========================
                        // WE CAN SEND DATA
                        // =========================
                        else if (key.isWritable()) {

                            SocketChannel client = (SocketChannel) key.channel();
                            Connection connection = (Connection) key.attachment();
                            ByteBuffer response = connection.responseBuffer;

                            client.write(response);

                            if (!response.hasRemaining()) {
                                if (connection.closeAfterWrite) {
                                    client.close();
                                    key.cancel();
                                } else {
                                    key.interestOps(SelectionKey.OP_READ);
                                }
                            }
                        }

                    } catch (Exception e) {
                        System.out.println("Client error: " + e.getMessage());
                        try {
                            key.channel().close();
                        } catch (IOException closeError) {
                            System.err.println("Cannot close client channel: " + closeError.getMessage());
                        }
                        key.cancel();
                    }
                }

            } catch (ClosedSelectorException e) {
                // Normal during shutdown: Server.stop() closes the selector.
                if (running) {
                    System.err.println("Loop error: " + e);
                }
            } catch (Exception e) {
                System.err.println("Loop error: " + e);
            }
        }
    }
}
