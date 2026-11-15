package com.localserver.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkedDecoderTest {

    @Test
    void decodesCompleteChunkedBody() {
        byte[] wire = "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        ChunkedDecoder decoder = new ChunkedDecoder(0, 1024);

        assertEquals(ChunkedDecoder.Status.COMPLETE, decoder.feed(wire, wire.length));
        assertEquals("hello world", new String(decoder.body(), StandardCharsets.UTF_8));
    }

    @Test
    void resumesWhenDataArrivesInPieces() {
        byte[] first = "5\r\nhel".getBytes(StandardCharsets.ISO_8859_1);
        byte[] second = "lo\r\n0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] cumulative = new byte[first.length + second.length];
        System.arraycopy(first, 0, cumulative, 0, first.length);
        System.arraycopy(second, 0, cumulative, first.length, second.length);

        ChunkedDecoder decoder = new ChunkedDecoder(0, 1024);

        assertEquals(ChunkedDecoder.Status.INCOMPLETE, decoder.feed(cumulative, first.length));
        assertEquals(ChunkedDecoder.Status.COMPLETE, decoder.feed(cumulative, cumulative.length));
        assertEquals("hello", new String(decoder.body(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsInvalidChunkSize() {
        byte[] wire = "zz\r\nhello\r\n0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        ChunkedDecoder decoder = new ChunkedDecoder(0, 1024);

        assertEquals(ChunkedDecoder.Status.BAD, decoder.feed(wire, wire.length));
    }

    @Test
    void enforcesBodyLimit() {
        byte[] wire = "6\r\nhello!\r\n0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        ChunkedDecoder decoder = new ChunkedDecoder(0, 4);

        assertEquals(ChunkedDecoder.Status.TOO_LARGE, decoder.feed(wire, wire.length));
    }
}
