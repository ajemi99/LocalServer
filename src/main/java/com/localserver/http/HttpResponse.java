package com.localserver.http;

import java.nio.charset.StandardCharsets;

import com.localserver.constants.Constants;

public class HttpResponse {

    public String status;
    public String contentType;
    public byte[] body;
    public String extraHeaders = "";      // ex: "Allow: GET\r\n" or "Location: /\r\n"

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

    // Builds the complete HTTP response: text headers + raw body bytes.
    public byte[] build() {
        String head = Constants.Http.VERSION_1_1 + " " + status + Constants.Http.CRLF
                + Constants.Http.HEADER_CONTENT_TYPE + ": " + contentType + Constants.Http.CRLF
                + Constants.Http.HEADER_CONTENT_LENGTH + ": " + body.length + Constants.Http.CRLF
                + extraHeaders
                + Constants.Http.CRLF;

        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
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
        String safe = escape(location);
        HttpResponse response = new HttpResponse(Constants.Http.REDIRECT_STATUS, Constants.Http.CONTENT_TYPE_HTML,
                "<html><body>Moved to <a href=\"" + safe + "\">"
                        + safe + "</a></body></html>");
        response.extraHeaders = Constants.Http.HEADER_LOCATION + ": " + location + Constants.Http.CRLF;
        return response;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
