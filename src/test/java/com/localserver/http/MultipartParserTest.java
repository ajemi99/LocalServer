package com.localserver.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipartParserTest {

    @Test
    void extractsFileNameAndRawBytes() {
        String body = "--B\r\n"
                + "Content-Disposition: form-data; name=\"f\"; filename=\"photo.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "\r\n"
                + "abc\r\n"
                + "--B--\r\n";

        List<MultipartParser.Part> parts = MultipartParser.parse(
                body.getBytes(StandardCharsets.ISO_8859_1), "B");

        assertEquals(1, parts.size());
        assertEquals("f", parts.get(0).name);
        assertEquals("photo.bin", parts.get(0).filename);
        assertEquals("abc", new String(parts.get(0).data, StandardCharsets.ISO_8859_1));
    }

    @Test
    void readsBoundaryWithQuotes() {
        assertEquals("abc", MultipartParser.boundaryOf("multipart/form-data; boundary=\"abc\""));
    }
}
