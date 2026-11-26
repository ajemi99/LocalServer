package com.localserver.http;

import java.util.Map;
import java.util.TreeMap;

public class CookieParser {

    private CookieParser() {}

    public static Map<String, Cookie> parseCookieHeader(String cookieHeader) {
        Map<String, Cookie> cookies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookies;
        }

        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            Cookie cookie = Cookie.parse(part);
            if (cookie != null) {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return cookies;
    }
}
