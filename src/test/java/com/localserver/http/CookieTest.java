package com.localserver.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookieTest {

    @Test
    void createsCookieWithNameAndValue() {
        Cookie cookie = new Cookie("sessionid", "abc123");
        assertEquals("sessionid", cookie.getName());
        assertEquals("abc123", cookie.getValue());
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new Cookie("", "value"));
        assertThrows(IllegalArgumentException.class, () -> new Cookie(null, "value"));
    }

    @Test
    void rejectsInvalidNameCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new Cookie("name with space", "value"));
        assertThrows(IllegalArgumentException.class, () -> new Cookie("name;with;semicolon", "value"));
        assertThrows(IllegalArgumentException.class, () -> new Cookie("name=with=equals", "value"));
        assertThrows(IllegalArgumentException.class, () -> new Cookie("name,with,comma", "value"));
    }

    @Test
    void setsDomain() {
        Cookie cookie = new Cookie("name", "value").setDomain("example.com");
        assertEquals("example.com", cookie.getDomain());
    }

    @Test
    void setsPath() {
        Cookie cookie = new Cookie("name", "value").setPath("/api");
        assertEquals("/api", cookie.getPath());
    }

    @Test
    void defaultsPathToRoot() {
        Cookie cookie = new Cookie("name", "value");
        assertEquals("/", cookie.getPath());
    }

    @Test
    void setsMaxAge() {
        Cookie cookie = new Cookie("name", "value").setMaxAge(3600);
        assertEquals(3600, cookie.getMaxAge());
    }

    @Test
    void setsExpires() {
        Cookie cookie = new Cookie("name", "value").setExpires(java.time.Instant.parse("2025-01-01T00:00:00Z"));
        assertEquals(java.time.Instant.parse("2025-01-01T00:00:00Z"), cookie.getExpires());
    }

    @Test
    void setsSecure() {
        Cookie cookie = new Cookie("name", "value").setSecure(true);
        assertTrue(cookie.isSecure());
    }

    @Test
    void setsHttpOnly() {
        Cookie cookie = new Cookie("name", "value").setHttpOnly(true);
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void setsSameSite() {
        Cookie cookie = new Cookie("name", "value").setSameSite("Strict");
        assertEquals("Strict", cookie.getSameSite());
        
        cookie.setSameSite("lax");
        assertEquals("Lax", cookie.getSameSite());
        
        cookie.setSameSite("NONE");
        assertEquals("None", cookie.getSameSite());
    }

    @Test
    void rejectsInvalidSameSite() {
        assertThrows(IllegalArgumentException.class, () -> new Cookie("name", "value").setSameSite("invalid"));
    }

    @Test
    void toHeaderIncludesAllAttributes() {
        Cookie cookie = new Cookie("sessionid", "abc123")
                .setDomain("example.com")
                .setPath("/")
                .setMaxAge(3600)
                .setSecure(true)
                .setHttpOnly(true)
                .setSameSite("Lax");
        
        String header = cookie.toHeader();
        assertTrue(header.contains("sessionid=abc123"));
        assertTrue(header.contains("Domain=example.com"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("Max-Age=3600"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
    }

    @Test
    void toHeaderOmitsNullDomain() {
        Cookie cookie = new Cookie("name", "value");
        String header = cookie.toHeader();
        assertFalse(header.contains("Domain"));
    }

    @Test
    void toHeaderUsesExpiresWhenMaxAgeNotSet() {
        Cookie cookie = new Cookie("name", "value")
                .setExpires(java.time.Instant.parse("2025-01-01T00:00:00Z"));
        
        String header = cookie.toHeader();
        assertTrue(header.contains("Expires="));
        assertFalse(header.contains("Max-Age"));
    }

    @Test
    void parsesSimpleCookie() {
        Cookie cookie = Cookie.parse("sessionid=abc123");
        assertNotNull(cookie);
        assertEquals("sessionid", cookie.getName());
        assertEquals("abc123", cookie.getValue());
    }

    @Test
    void parsesCookieWithSpaces() {
        Cookie cookie = Cookie.parse("  sessionid = abc123  ");
        assertNotNull(cookie);
        assertEquals("sessionid", cookie.getName());
        assertEquals("abc123", cookie.getValue());
    }

    @Test
    void parseReturnsNullForInvalidInput() {
        assertNull(Cookie.parse(null));
        assertNull(Cookie.parse(""));
        assertNull(Cookie.parse("noequals"));
        assertNull(Cookie.parse("=value"));
    }

    @Test
    void encodesSpecialCharacters() {
        Cookie cookie = new Cookie("name", "value with spaces & special");
        String header = cookie.toHeader();
        assertTrue(header.contains("value+with+spaces+%26+special"));
    }
}
