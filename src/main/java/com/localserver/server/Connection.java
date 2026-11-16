package com.localserver.server;

import com.localserver.http.ChunkedDecoder;
import com.localserver.http.HttpRequest;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.localserver.constants.Constants;

/**
 * Per-client connection state for the single-threaded event loop.
 */
public class Connection {

    static final int INITIAL_SIZE = Constants.Server.CONNECTION_INITIAL_SIZE;       // starting size of the request buffer
    static final int SHRINK_ABOVE = Constants.Server.CONNECTION_SHRINK_ABOVE;  // shrink after a large upload

    // Small buffer used ONLY to read from the socket (one chunk at a time)
    ByteBuffer requestBuffer = ByteBuffer.allocate(Constants.Server.CONNECTION_INITIAL_SIZE);

    // Response currently being sent
    ByteBuffer responseBuffer = ByteBuffer.allocate(0);

    int localPort;

    // ---- The growing buffer: everything the client sent for the current request ----
    byte[] data = new byte[INITIAL_SIZE];
    int length = 0;                  // number of useful bytes in data

    // ---- Current request state (avoid recomputing on every chunk) ----
    int headerEnd = -1;              // end of headers (-1 = not received yet)
    HttpRequest pending = null;      // request whose headers were already parsed
    int contentLength = 0;           // body size announced by the client
    ChunkedDecoder decoder = null;   // non-null if the client sends a "chunked" body

    // If true: send the response then close the connection (e.g. 413, remaining body is not read)
    boolean closeAfterWrite = false;

    // Appends received bytes to the buffer (which grows if needed)
    void append(ByteBuffer src) {
        int n = src.remaining();
        if (length + n > data.length) {
            data = Arrays.copyOf(data, Math.max(length + n, data.length * 2));
        }
        src.get(data, length, n);
        length += n;
    }

    // Prepares the connection for the next request (keep-alive)
    void reset() {
        if (data.length > SHRINK_ABOVE) {
            data = new byte[INITIAL_SIZE];      // release the memory of a large upload
        }
        length = 0;
        headerEnd = -1;
        pending = null;
        contentLength = 0;
        decoder = null;
    }
}
