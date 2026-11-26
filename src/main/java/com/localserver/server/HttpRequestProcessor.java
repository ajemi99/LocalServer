package com.localserver.server;

import com.localserver.config.ServerConfig;
import com.localserver.http.BadRequestException;
import com.localserver.http.ChunkedDecoder;
import com.localserver.http.Cookie;
import com.localserver.http.ErrorPages;
import com.localserver.http.HttpParser;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;
import com.localserver.http.session.Session;
import com.localserver.http.session.SessionManager;
import com.localserver.logging.LoggingManger;
import com.localserver.routing.Router;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;

import com.localserver.constants.Constants;

public class HttpRequestProcessor {

    private static final int MAX_HEADER_SIZE = Constants.Server.MAX_HEADER_SIZE;

    private final SessionManager sessionManager;

    public HttpRequestProcessor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void process(SelectionKey key, List<ServerConfig> servers) throws IOException {
        sessionManager.cleanupExpiredSessions();
        SocketChannel client = (SocketChannel) key.channel();
        Connection connection = (Connection) key.attachment();
        ByteBuffer buffer = connection.requestBuffer;

        int bytesRead = client.read(buffer);

        if (bytesRead == -1) {
            closeClient(key, client);
            return;
        }
        connection.appendRequestBytes();

        if (connection.headerEnd == -1) {
            connection.headerEnd = HttpParser.findHeaderEnd(connection.data, connection.length);

            boolean tooLarge = connection.headerEnd == -1
                    ? connection.length > MAX_HEADER_SIZE
                    : connection.headerEnd > MAX_HEADER_SIZE;

            if (tooLarge) {
                connection.responseBuffer = ByteBuffer.wrap(ErrorPages.get(413).build());
                connection.closeAfterWrite = true;
                key.interestOps(SelectionKey.OP_WRITE);
                return;
            }

            if (connection.headerEnd == -1) {
                LoggingManger.fine("Request not complete yet...");
                return;
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

                String transferEncoding = connection.pending.headers
                        .get(Constants.Http.HEADER_TRANSFER_ENCODING);
                if (transferEncoding != null) {
                    if (!transferEncoding.trim().equalsIgnoreCase("chunked")
                            || connection.pending.headers
                                    .containsKey(Constants.Http.HEADER_CONTENT_LENGTH)) {
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
                    return;
                }
            }

            int bodyStart = connection.headerEnd + 4;
            HttpRequest httpRequest = connection.pending;

            if (connection.decoder != null) {
                ChunkedDecoder.Status status = connection.decoder.feed(connection.data,
                        connection.length);

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
                    return;
                }
                if (status == ChunkedDecoder.Status.INCOMPLETE) {
                    LoggingManger.fine("Chunked body not complete yet...");
                    return;
                }

                httpRequest.body = connection.decoder.body();
                httpRequest.headers.remove(Constants.Http.HEADER_TRANSFER_ENCODING);
                httpRequest.headers.put(Constants.Http.HEADER_CONTENT_LENGTH,
                        String.valueOf(httpRequest.body.length));
            } else {
                int available = connection.length - bodyStart;
                if (available < connection.contentLength) {
                    LoggingManger.fine("Body not complete yet... ("
                            + available + "/" + connection.contentLength + ")");
                    return;
                }
                httpRequest.body = Arrays.copyOfRange(
                        connection.data, bodyStart, bodyStart + connection.contentLength);
            }

            // Resolve session before routing
            resolveSession(httpRequest);

            LoggingManger.fine(httpRequest.method + " " + httpRequest.path);

            httpResponse = Router.route(httpRequest, connection.localPort, servers);

            // Add session cookie if new session was created
            if (httpRequest.session != null && httpRequest.session.isNew()) {
                httpResponse.addCookie(sessionManager.createSessionCookie(httpRequest.session));
            }

        } catch (BadRequestException e) {
            LoggingManger.warning("Bad request: " + e.getMessage());
            httpResponse = ErrorPages.get(400);
        } catch (Exception e) {
            LoggingManger.severe("Error while handling request", e);
            httpResponse = ErrorPages.get(500);
        }

        connection.responseBuffer = ByteBuffer.wrap(httpResponse.build());
        connection.reset();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void resolveSession(HttpRequest request) {
        String sessionCookieName = sessionManager.getSessionCookieName();
        Cookie sessionCookie = request.cookies != null ? request.cookies.get(sessionCookieName) : null;
        
        if (sessionCookie != null) {
            String sessionId = sessionCookie.getValue();
            Session session = sessionManager.getSession(sessionId);
            if (session != null) {
                request.session = session;
                return;
            }
        }
        
        // Create new session
        request.session = sessionManager.createSession();
    }

    private static void closeClient(SelectionKey key, SocketChannel client) throws IOException {
        client.close();
        key.cancel();
    }
}
