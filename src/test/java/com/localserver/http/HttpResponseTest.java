package com.localserver.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseTest {

    @Test
    void buildsHeadersAndBodyAsBytes() {
        HttpResponse response = new HttpResponse("200 OK", "text/plain; charset=UTF-8", "hello");

        String wire = new String(response.build(), StandardCharsets.UTF_8);

        assertTrue(wire.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(wire.contains("Content-Type: text/plain; charset=UTF-8\r\n"));
        assertTrue(wire.contains("Content-Length: 5\r\n"));
        assertTrue(wire.endsWith("\r\n\r\nhello"));
    }

    @Test
    void buildsRedirectWithLocationHeader() {
        HttpResponse response = HttpResponse.redirect("/new place");

        assertEquals("301 Moved Permanently", response.status);
        assertEquals("Location: /new place\r\n", response.extraHeaders);
        assertTrue(new String(response.body, StandardCharsets.UTF_8).contains("/new place"));
    }

    @Test
    void normalizesUnknownErrorCodeTo500() {
        assertEquals("500 Internal Server Error", ErrorPages.get(999).status);
    }
}
