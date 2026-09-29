import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class Main {

    public static void main(String[] args) throws Exception {

        // 1. Create Selector
        Selector selector = Selector.open();

        // 2. Create server
        ServerSocketChannel server =
                ServerSocketChannel.open();

        // 3. Listen on port 8080
        server.bind(new InetSocketAddress(8080));

        // 4. Non-blocking
        server.configureBlocking(false);

        // 5. Watch for new clients
        server.register(
                selector,
                SelectionKey.OP_ACCEPT
        );

        System.out.println("Server started on port 8080");

        // 6. Main loop
        while (true) {

            // Wait for an event
            selector.select();

            // Get events
            Iterator<SelectionKey> iterator =
                    selector.selectedKeys().iterator();

            while (iterator.hasNext()) {

                SelectionKey key =
                        iterator.next();

                // Remove event after taking it
                iterator.remove();

                // =========================
                // NEW CLIENT
                // =========================

                if (key.isAcceptable()) {

                    ServerSocketChannel serverChannel =
                            (ServerSocketChannel) key.channel();

                    SocketChannel client =
                            serverChannel.accept();

                    client.configureBlocking(false);

                    // Tell selector to watch this client
                    SelectionKey clientKey =
                            client.register(
                                    selector,
                                    SelectionKey.OP_READ
                            );

                    ByteBuffer buffer =
                            ByteBuffer.allocate(8192);

                    clientKey.attach(buffer);

                    System.out.println(
                            "Client connected: "
                            + client.getRemoteAddress()
                    );
                }

                // =========================
                // CLIENT SENT DATA
                // =========================

                else if (key.isReadable()) {

                    SocketChannel client =
                                (SocketChannel) key.channel();

                        ByteBuffer buffer =
                                (ByteBuffer) key.attachment();

                        int bytesRead =
                                client.read(buffer);

                    System.out.println(
                            "Bytes received: "
                            + bytesRead
                    );

                    if (bytesRead == -1) {
                        client.close();
                        continue;
                    }

                    buffer.flip();

                    String request =
                            StandardCharsets.UTF_8
                                    .decode(buffer)
                                    .toString();

                    System.out.println("----- REQUEST -----");
                    System.out.println(request);
                    System.out.println("-------------------");

                    buffer.clear();

                    String body = "Hello from LocalServer";

            String response =
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " +
                    body.getBytes(StandardCharsets.UTF_8).length +
                    "\r\n" +
                    "\r\n" +
                    body;

            ByteBuffer responseBuffer =
                    StandardCharsets.UTF_8.encode(response);

            client.write(responseBuffer);
                }
            }
        }
    }
}