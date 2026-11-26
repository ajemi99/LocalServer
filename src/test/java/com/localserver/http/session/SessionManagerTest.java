package com.localserver.http.session;
import com.localserver.http.Cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void createsSessionWithUniqueId() {
        SessionManager manager = new SessionManager();
        Session session1 = manager.createSession();
        Session session2 = manager.createSession();
        
        assertNotNull(session1.getId());
        assertNotNull(session2.getId());
        assertNotEquals(session1.getId(), session2.getId());
        assertEquals(64, session1.getId().length()); // 64 hex chars for 32 bytes
    }

    @Test
    void getsExistingSession() {
        SessionManager manager = new SessionManager();
        Session created = manager.createSession();
        
        Session retrieved = manager.getSession(created.getId());
        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
    }

    @Test
    void returnsNullForNonExistentSession() {
        SessionManager manager = new SessionManager();
        Session session = manager.getSession("nonexistent");
        assertNull(session);
    }

    @Test
    void returnsNullForNullSessionId() {
        SessionManager manager = new SessionManager();
        Session session = manager.getSession(null);
        assertNull(session);
    }

    @Test
    void removesSession() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        
        manager.removeSession(session.getId());
        Session retrieved = manager.getSession(session.getId());
        assertNull(retrieved);
    }

    @Test
    void sessionIsNewInitially() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        assertTrue(session.isNew());
    }

    @Test
    void sessionIsNotNewAfterRetrieval() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        
        manager.getSession(session.getId());
        assertFalse(session.isNew());
    }

    @Test
    void sessionExpiresAfterMaxInactiveInterval() throws InterruptedException {
        SessionManager manager = new SessionManager(1); // 1 second timeout
        
        Session session = manager.createSession();
        Thread.sleep(1500); // Wait for expiration
        
        Session retrieved = manager.getSession(session.getId());
        assertNull(retrieved);
    }

    @Test
    void createsSessionCookie() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        
        Cookie cookie = manager.createSessionCookie(session);
        assertNotNull(cookie);
        assertEquals(manager.getSessionCookieName(), cookie.getName());
        assertEquals(session.getId(), cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());
    }

    @Test
    void createsInvalidateSessionCookie() {
        SessionManager manager = new SessionManager();
        Cookie cookie = manager.createInvalidateSessionCookie();
        
        assertNotNull(cookie);
        assertEquals(manager.getSessionCookieName(), cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge());
    }

    @Test
    void sessionAttributesCanBeSetAndRetrieved() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        
        session.setAttribute("username", "testuser");
        assertEquals("testuser", session.getAttribute("username"));
        
        session.setAttribute("age", 25);
        assertEquals(25, session.getAttribute("age"));
    }

    @Test
    void removingAttributeWorks() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        
        session.setAttribute("key", "value");
        session.removeAttribute("key");
        assertNull(session.getAttribute("key"));
    }

    @Test
    void invalidatingSessionClearsAttributes() {
        SessionManager manager = new SessionManager();
        Session session = manager.createSession();
        
        session.setAttribute("key", "value");
        session.invalidate();
        
        assertTrue(session.isInvalidated());
        assertThrows(IllegalStateException.class, () -> session.getAttribute("key"));
    }

    @Test
    void getMaxInactiveIntervalReturnsConfiguredValue() {
        SessionManager manager = new SessionManager(600);
        assertEquals(600, manager.getMaxInactiveInterval());
    }

    @Test
    void sessionCookieNameIsConsistent() {
        SessionManager manager = new SessionManager();
        assertEquals("JSESSIONID", manager.getSessionCookieName());
    }
}
