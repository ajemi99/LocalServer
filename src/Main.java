import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class Main {

    public static void main(String[] args) throws Exception {

        // 1. Selector
        Selector selector = Selector.open();
        int[] ports = {8080, 8081, 9090};

        for (int port : ports) {
        try {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                server.bind(new InetSocketAddress(port));
                server.configureBlocking(false);
                server.register(selector, SelectionKey.OP_ACCEPT);
                System.out.println("Listening on port " + port);
        } catch (IOException e) {
                // port mchghoul: ma-n7bsouch serveur kaml
                System.err.println("Cannot bind port " + port + ": " + e.getMessage());
        }
        }

        // 3. Main loop: ma-kaytwqfch abadan
        while (true) {
            try {
                selector.select(1000);

                Iterator<SelectionKey> iterator =
                        selector.selectedKeys().iterator();

                while (iterator.hasNext()) {

                    SelectionKey key = iterator.next();
                    iterator.remove();

                    // try/catch 3la kol client: ghalta wa7da ma-t9tlch serveur
                    try {

                        if (!key.isValid()) {
                            continue;
                        }

                        // =========================
                        // NEW CLIENT
                        // =========================
                        if (key.isAcceptable()) {

                            ServerSocketChannel serverChannel =
                                    (ServerSocketChannel) key.channel();

                            SocketChannel client = serverChannel.accept();
                            if (client == null) {
                                continue;
                            }

                            client.configureBlocking(false);

                            SelectionKey clientKey =
                                    client.register(selector, SelectionKey.OP_READ);
                            clientKey.attach(new ClientConnection());

                            System.out.println(
                                    "Client connected: " + client.getRemoteAddress());
                        }

                        // =========================
                        // CLIENT SENT DATA
                        // =========================
                        else if (key.isReadable()) {

                            SocketChannel client = (SocketChannel) key.channel();
                            ClientConnection connection =
                                    (ClientConnection) key.attachment();
                            ByteBuffer buffer = connection.requestBuffer;

                            int bytesRead = client.read(buffer);

                            if (bytesRead == -1) {
                                client.close();
                                key.cancel();
                                continue;
                            }

                            buffer.flip();

                            // duplicate(): l'buffer l'asli ybqa saliim
                            String request = StandardCharsets.UTF_8
                                    .decode(buffer.duplicate())
                                    .toString();

                            // Headers mazal ma-wslouch
                            if (!request.contains("\r\n\r\n")) {

                                buffer.compact();

                                // Buffer 3amer w mazal ma-lqina fin-at l'headers => 413
                                if (!buffer.hasRemaining()) {
                                    connection.responseBuffer = StandardCharsets.UTF_8
                                            .encode(ErrorPages.get(413).build());
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

                                // Body mazal ma-wsl kaml
                                if (!HttpParser.isBodyComplete(httpRequest)) {

                                    buffer.compact();

                                    if (!buffer.hasRemaining()) {
                                        httpResponse = ErrorPages.get(413);
                                        buffer.clear();
                                        connection.responseBuffer = StandardCharsets.UTF_8
                                                .encode(httpResponse.build());
                                        key.interestOps(SelectionKey.OP_WRITE);
                                    } else {
                                        System.out.println("Body not complete yet...");
                                    }
                                    continue;
                                }

                                System.out.println("----- REQUEST -----");
                                System.out.println(request);
                                System.out.println("-------------------");

                                httpResponse = Router.route(httpRequest);

                            } catch (BadRequestException e) {
                                httpResponse = ErrorPages.get(400);
                            } catch (Exception e) {
                                e.printStackTrace();
                                httpResponse = ErrorPages.get(500);
                            }

                            connection.responseBuffer = StandardCharsets.UTF_8
                                    .encode(httpResponse.build());

                            buffer.clear();
                            key.interestOps(SelectionKey.OP_WRITE);
                        }

                        // =========================
                        // WE CAN SEND DATA
                        // =========================
                        else if (key.isWritable()) {

                            SocketChannel client = (SocketChannel) key.channel();
                            ClientConnection connection =
                                    (ClientConnection) key.attachment();
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
                // Erreur f l'boucle nfsha: kanktbouha w kankmlou
                System.err.println("Loop error: " + e);
            }
        }
    }
}