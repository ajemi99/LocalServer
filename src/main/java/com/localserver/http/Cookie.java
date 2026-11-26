package com.localserver.http;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Cookie {

    private final String name;
    private final String value;
    private String domain;
    private String path = "/";
    private long maxAge = -1;
    private Instant expires;
    private boolean secure;
    private boolean httpOnly;
    private String sameSite = "Lax";

    public Cookie(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Cookie name cannot be null or empty");
        }
        if (name.contains(" ") || name.contains(",") || name.contains(";") || name.contains("=")
                || hasControlChars(name)) {
            throw new IllegalArgumentException("Cookie name contains invalid character");
        }
        String v = value != null ? value : "";
        if (hasControlChars(v)) {
            throw new IllegalArgumentException("Cookie value contains invalid character");
        }
        this.name = name;
        this.value = v;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Cookie setDomain(String domain) {
        if (domain != null && hasControlChars(domain)) {
            throw new IllegalArgumentException("Cookie domain contains invalid character");
        }
        this.domain = domain;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public Cookie setPath(String path) {
        String p = path != null ? path : "/";
        if (hasControlChars(p)) {
            throw new IllegalArgumentException("Cookie path contains invalid character");
        }
        this.path = p;
        return this;
    }

    public String getPath() {
        return path;
    }

    public Cookie setMaxAge(long maxAge) {
        this.maxAge = maxAge;
        this.expires = null;
        return this;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public Cookie setExpires(Instant expires) {
        this.expires = expires;
        this.maxAge = -1;
        return this;
    }

    public Instant getExpires() {
        return expires;
    }

    public Cookie setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public Cookie setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public Cookie setSameSite(String sameSite) {
        if (sameSite != null) {
            String lower = sameSite.toLowerCase(Locale.ROOT);
            if (!lower.equals("strict") && !lower.equals("lax") && !lower.equals("none")) {
                throw new IllegalArgumentException("Invalid SameSite value: " + sameSite);
            }
            this.sameSite = lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
        }
        return this;
    }

    public String getSameSite() {
        return sameSite;
    }

    public String toHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(value, StandardCharsets.UTF_8));

        if (domain != null && !domain.isEmpty()) {
            sb.append("; Domain=").append(domain);
        }
        sb.append("; Path=").append(path);

        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        } else if (expires != null) {
            String formatted = DateTimeFormatter.RFC_1123_DATE_TIME
                    .withLocale(Locale.US)
                    .withZone(java.time.ZoneOffset.UTC)
                    .format(expires);
            sb.append("; Expires=").append(formatted);
        }

        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (sameSite != null) {
            sb.append("; SameSite=").append(sameSite);
        }
        return sb.toString();
    }

    public static Cookie parse(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        int eq = header.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String name = URLDecoder.decode(header.substring(0, eq).trim(), StandardCharsets.UTF_8);
        String value = URLDecoder.decode(header.substring(eq + 1).trim(), StandardCharsets.UTF_8);
        return new Cookie(name, value);
    }

    private static boolean hasControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return toHeader();
    }
}
