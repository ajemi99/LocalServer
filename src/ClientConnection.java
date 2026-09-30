import java.nio.ByteBuffer;

public class ClientConnection {

    ByteBuffer requestBuffer;
    ByteBuffer responseBuffer;

    public ClientConnection() {

        requestBuffer =
                ByteBuffer.allocate(8192);

        responseBuffer =
                ByteBuffer.allocate(8192);
    }
}