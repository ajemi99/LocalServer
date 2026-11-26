package com.localserver.http;

import com.localserver.http.session.Session;

import java.util.Map;

public class HttpRequest {

    public String method;
    public String path;
    public String version;

    public Map<String, String> headers;

    // Raw bytes, never converted to text (otherwise binary files are corrupted)
    public byte[] body = new byte[0];

    // Parsed cookies from Cookie header
    public Map<String, Cookie> cookies;

    // Session associated with this request (may be null)
    public Session session;
}
