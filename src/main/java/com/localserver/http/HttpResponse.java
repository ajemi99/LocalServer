package com.localserver.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.localserver.constants.Constants;

public class HttpResponse {

    public String status;
    public String contentType;
    public byte[] body;
    public String extraHeaders = "";      // ex: "Allow: GET\r\n" or "Location: /\r\n"

    // Set-Cookie headers are stored separately to allow multiple
    private final List<String> setCookieHeaders = new ArrayList<>();

    // For text (ErrorPages, etc.)
    public HttpResponse(String status, String contentType, String body) {
        this(status, contentType, body.getBytes(Constants.Http.UTF_8));
    }

    // For bytes (files, images...)
    public HttpResponse(String status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    public void addCookie(Cookie cookie) {
        if (cookie != null) {
            setCookieHeaders.add(cookie.toHeader());
        }
    }

    public List<String> getSetCookieHeaders() {
        return setCookieHeaders;
    }

    // Builds the complete HTTP response: text headers + raw body bytes.
    public byte[] build() {
        StringBuilder head = new StringBuilder();
        head.append(Constants.Http.VERSION_1_1).append(' ').append(status).append(Constants.Http.CRLF);
        head.append(Constants.Http.HEADER_CONTENT_TYPE).append(": ").append(contentType).append(Constants.Http.CRLF);
        head.append(Constants.Http.HEADER_CONTENT_LENGTH).append(": ").append(body.length).append(Constants.Http.CRLF);
        head.append(extraHeaders);
        
        for (String cookieHeader : setCookieHeaders) {
            head.append(Constants.Http.HEADER_SET_COOKIE).append(": ").append(cookieHeader).append(Constants.Http.CRLF);
        }
        
        head.append(Constants.Http.CRLF);

        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[headBytes.length + body.length];
        System.arraycopy(headBytes, 0, all, 0, headBytes.length);
        System.arraycopy(body, 0, all, headBytes.length, body.length);
        return all;
    }

    /**
     * Builds the redirect response used by route redirects and directory
     * trailing-slash redirects.
     */
    public static HttpResponse redirect(String location) {
        return redirect(location, Constants.Http.DEFAULT_REDIRECT_STATUS);
    }

    public static HttpResponse redirect(String location, int status) {
        String safe = escape(location);
        String statusLine = status + " " + Constants.Status.reasonFor(status);
        HttpResponse response = new HttpResponse(statusLine, Constants.Http.CONTENT_TYPE_HTML,
                "<html><body>Moved to <a href=\"" + safe + "\">"
                        + safe + "</a></body></html>");
        response.extraHeaders = Constants.Http.HEADER_LOCATION + ": " + location + Constants.Http.CRLF;
        return response;
    }

    private static String escape(String s) {
        return s.replace("&", "&").replace("<", "<")
                .replace(">", ">").replace("\"", "\u0026quot;");
    }
}
