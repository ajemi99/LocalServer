package com.localserver.http.session;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Session {

    private final String id;
    private final Instant createdAt;
    private Instant lastAccessedAt;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private boolean isNew;
    private boolean invalidated = false;

    Session(String id) {
        this.id = id;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.isNew = true;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public boolean isNew() {
        return isNew;
    }

    void setNotNew() {
        this.isNew = false;
    }

    public Object getAttribute(String name) {
        if (invalidated) {
            throw new IllegalStateException("Session is invalidated");
        }
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        if (invalidated) {
            throw new IllegalStateException("Session is invalidated");
        }
        if (value == null) {
            removeAttribute(name);
        } else {
            attributes.put(name, value);
        }
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void invalidate() {
        this.invalidated = true;
        attributes.clear();
    }

    public boolean isInvalidated() {
        return invalidated;
    }

    boolean isExpired(long maxInactiveIntervalSeconds) {
        if (maxInactiveIntervalSeconds <= 0) {
            return false;
        }
        return Instant.now().isAfter(lastAccessedAt.plusSeconds(maxInactiveIntervalSeconds));
    }
}
