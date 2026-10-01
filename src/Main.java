import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {

        // 1. Config : validée au démarrage
        String configPath = args.length > 0 ? args[0] : "config.json";
        List<ServerConfig> servers;
        try {
            servers = ConfigLoader.load(configPath);
        } catch (Exception e) {
            System.err.println("Config error (" + configPath + "): " + e.getMessage());
            return;
        }

        // port -> host (un seul channel par port)
        Map<Integer, String> portToHost = new LinkedHashMap<>();
        for (ServerConfig sc : servers) {
            for (int port : sc.ports) {
                portToHost.putIfAbsent(port, sc.host);
            }
        }

        // 2. Selector
        Selector selector = Selector.open();

        // 3. Un ServerSocketChannel par port
        for (Map.Entry<Integer, String> e : portToHost.entrySet()) {
            try {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                server.bind(new InetSocketAddress(e.getValue(), e.getKey()));
                server.configureBlocking(false);
                server.register(selector, SelectionKey.OP_ACCEPT);
                System.out.println("Listening on " + e.getValue() + ":" + e.getKey());
            } catch (IOException ex) {
                System.err.println("Cannot bind port " + e.getKey() + ": " + ex.getMessage());
            }
        }

        if (selector.keys().isEmpty()) {
            System.err.println("No port could be opened. Exiting.");
            return;
        }

        // 4. Boucle principale : ne s'arrête jamais
        while (true) {
            try {
                selector.select(1000);

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {

                    SelectionKey key = iterator.next();
                    iterator.remove();

                    // try/catch par client : une erreur ne tue pas le serveur
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

                            ClientConnection connection = new ClientConnection();
                            // sur QUEL port ce client est arrivé (sert à choisir le serveur)
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
                            ClientConnection connection = (ClientConnection) key.attachment();
                            ByteBuffer buffer = connection.requestBuffer;

                            int bytesRead = client.read(buffer);

                            if (bytesRead == -1) {
                                client.close();
                                key.cancel();
                                continue;
                            }

                            buffer.flip();

                            String request = StandardCharsets.UTF_8
                                    .decode(buffer.duplicate())
                                    .toString();

                            // Headers pas encore complets
                            if (!request.contains("\r\n\r\n")) {

                                buffer.compact();

                                if (!buffer.hasRemaining()) {
                                    connection.responseBuffer =
                                            ByteBuffer.wrap(ErrorPages.get(413).build());
                                    buffer.clear();
                                    key.interestOps(SelectionKey.OP_WRITE);
                                } else {
                                    System.out.println("Request not complete yet...");
                                }
                                continue;
                            }

                            HttpResponse httpResponse;

                            try {
                                HttpRequest httpRequest = HttpParser.parse(request);

                                // Body pas encore complet
                                if (!HttpParser.isBodyComplete(httpRequest)) {

                                    buffer.compact();

                                    if (!buffer.hasRemaining()) {
                                        connection.responseBuffer =
                                                ByteBuffer.wrap(ErrorPages.get(413).build());
                                        buffer.clear();
                                        key.interestOps(SelectionKey.OP_WRITE);
                                    } else {
                                        System.out.println("Body not complete yet...");
                                    }
                                    continue;
                                }

                                System.out.println(httpRequest.method + " " + httpRequest.path);

                                httpResponse = Router.route(httpRequest, connection.localPort, servers);

                            } catch (BadRequestException e) {
                                httpResponse = ErrorPages.get(400);
                            } catch (Exception e) {
                                e.printStackTrace();
                                httpResponse = ErrorPages.get(500);
                            }

                            // build() retourne des octets : pas de conversion texte, les images passent
                            connection.responseBuffer = ByteBuffer.wrap(httpResponse.build());

                            buffer.clear();
                            key.interestOps(SelectionKey.OP_WRITE);
                        }

                        // =========================
                        // WE CAN SEND DATA
                        // =========================
                        else if (key.isWritable()) {

                            SocketChannel client = (SocketChannel) key.channel();
                            ClientConnection connection = (ClientConnection) key.attachment();
                            ByteBuffer response = connection.responseBuffer;

                            client.write(response);

                            if (!response.hasRemaining()) {
                                key.interestOps(SelectionKey.OP_READ);
                            }
                        }

                    } catch (Exception e) {
                        System.out.println("Client error: " + e.getMessage());
                        try {
                            key.channel().close();
                        } catch (IOException ignored) {
                        }
                        key.cancel();
                    }
                }

            } catch (Exception e) {
                System.err.println("Loop error: " + e);
            }
        }
    }
}