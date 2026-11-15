package com.localserver.http;

import java.util.Map;

public class HttpRequest {

    public String method;
    public String path;
    public String version;

    public Map<String, String> headers;

    // Raw bytes, never converted to text (otherwise binary files are corrupted)
    public byte[] body = new byte[0];
}
