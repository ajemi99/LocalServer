package com.localserver.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpParserTest {

    @Test
    void parsesRequestLineHeadersAndRawBody() {
        byte[] raw = ("POST /submit?x=1 HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 5\r\n"
                + "\r\n"
                + "hello").getBytes(StandardCharsets.ISO_8859_1);

        HttpRequest request = HttpParser.parse(raw);

        assertEquals("POST", request.method);
        assertEquals("/submit?x=1", request.path);
        assertEquals("HTTP/1.1", request.version);
        assertEquals("localhost", request.headers.get("host"));
        assertArrayEquals("hello".getBytes(StandardCharsets.ISO_8859_1), request.body);
    }

    @Test
    void rejectsHttp11WithoutHost() {
        byte[] raw = "GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThrows(BadRequestException.class, () -> HttpParser.parse(raw));
    }

    @Test
    void reportsBodyCompletionFromContentLength() {
        byte[] raw = ("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\n\r\n"
                + "data").getBytes(StandardCharsets.ISO_8859_1);

        HttpRequest request = HttpParser.parse(raw);

        assertEquals(4, HttpParser.getContentLength(request));
        assertEquals(true, HttpParser.isBodyComplete(request));
    }
}
