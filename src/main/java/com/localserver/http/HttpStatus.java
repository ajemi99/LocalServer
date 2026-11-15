package com.localserver.http;

/**
 * HTTP status codes and reason phrases supported by LocalServer.
 */
public final class HttpStatus {

    private HttpStatus() {
    }

    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * Keeps the status codes known by the default error-page generator and maps
     * anything else to 500, as the original implementation did.
     */
    public static int normalize(int code) {
        return switch (code) {
            case BAD_REQUEST, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED, PAYLOAD_TOO_LARGE -> code;
            default -> INTERNAL_SERVER_ERROR;
        };
    }

    public static String reason(int code) {
        return switch (normalize(code)) {
            case BAD_REQUEST -> "Bad Request";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Not Found";
            case METHOD_NOT_ALLOWED -> "Method Not Allowed";
            case PAYLOAD_TOO_LARGE -> "Payload Too Large";
            default -> "Internal Server Error";
        };
    }
}
