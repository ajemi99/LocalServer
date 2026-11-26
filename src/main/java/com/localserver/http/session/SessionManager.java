package com.localserver.http.session;
import com.localserver.http.Cookie;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";
    private static final int DEFAULT_MAX_INACTIVE_INTERVAL = 1800;
    private static final int SESSION_ID_BYTES = 32;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final int maxInactiveInterval;

    public SessionManager() {
        this(DEFAULT_MAX_INACTIVE_INTERVAL);
    }

    public SessionManager(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public Session createSession() {
        String id = generateSessionId();
        Session session = new Session(id);
        sessions.put(id, session);
        return session;
    }

    public Session getSession(String id) {
        if (id == null) {
            return null;
        }
        Session session = sessions.get(id);
        if (session != null) {
            if (session.isExpired(maxInactiveInterval)) {
                sessions.remove(id);
                return null;
            }
            session.setLastAccessedAt(Instant.now());
            session.setNotNew();
        }
        return session;
    }

    public void removeSession(String id) {
        sessions.remove(id);
    }

    public String getSessionCookieName() {
        return SESSION_COOKIE_NAME;
    }

    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public Cookie createSessionCookie(Session session) {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, session.getId());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setSameSite("Lax");
        cookie.setMaxAge(maxInactiveInterval);
        return cookie;
    }

    public Cookie createInvalidateSessionCookie() {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setSameSite("Lax");
        cookie.setMaxAge(0);
        return cookie;
    }

    private String generateSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> {
            Session s = entry.getValue();
            return s.isExpired(maxInactiveInterval);
        });
    }

    public void close() {
    }
}
