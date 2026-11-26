package com.localserver.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CookieParserTest {

    @Test
    void parsesSimpleCookie() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("sessionid=abc123");
        assertEquals(1, cookies.size());
        assertNotNull(cookies.get("sessionid"));
        assertEquals("abc123", cookies.get("sessionid").getValue());
    }

    @Test
    void parsesMultipleCookies() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("sessionid=abc123; theme=dark; lang=en");
        assertEquals(3, cookies.size());
        assertEquals("abc123", cookies.get("sessionid").getValue());
        assertEquals("dark", cookies.get("theme").getValue());
        assertEquals("en", cookies.get("lang").getValue());
    }

    @Test
    void handlesWhitespace() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("  sessionid = abc123  ;  theme = dark  ");
        assertEquals(2, cookies.size());
        assertEquals("abc123", cookies.get("sessionid").getValue());
        assertEquals("dark", cookies.get("theme").getValue());
    }

    @Test
    void returnsEmptyMapForNullInput() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader(null);
        assertNotNull(cookies);
        assertTrue(cookies.isEmpty());
    }

    @Test
    void returnsEmptyMapForEmptyInput() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("");
        assertNotNull(cookies);
        assertTrue(cookies.isEmpty());
    }

    @Test
    void ignoresMalformedCookies() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("valid=value; invalid; =noName");
        assertEquals(1, cookies.size());
        assertNotNull(cookies.get("valid"));
        assertEquals("value", cookies.get("valid").getValue());
    }

    @Test
    void handlesCookieWithEqualsInValue() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("data=a=b=c");
        assertEquals(1, cookies.size());
        assertEquals("a=b=c", cookies.get("data").getValue());
    }

    @Test
    void isCaseInsensitiveForKeyLookup() {
        Map<String, Cookie> cookies = CookieParser.parseCookieHeader("SessionID=abc123");
        assertNotNull(cookies.get("sessionid"));
        assertNotNull(cookies.get("SESSIONID"));
    }
}
