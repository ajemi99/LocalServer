package com.localserver.http;

import com.localserver.constants.Constants;

/**
 * HTTP status codes and reason phrases supported by LocalServer.
 */
public final class HttpStatus {

    private HttpStatus() {
    }

    /**
     * Keeps the status codes known by the default error-page generator and maps
     * anything else to 500, as the original implementation did.
     */
    public static int normalize(int code) {
        return switch (code) {
            case Constants.Status.BAD_REQUEST, Constants.Status.FORBIDDEN, Constants.Status.NOT_FOUND,
                 Constants.Status.METHOD_NOT_ALLOWED, Constants.Status.PAYLOAD_TOO_LARGE -> code;
            default -> Constants.Status.INTERNAL_SERVER_ERROR;
        };
    }

    public static String reason(int code) {
        return switch (normalize(code)) {
            case Constants.Status.BAD_REQUEST -> Constants.Status.REASON_BAD_REQUEST;
            case Constants.Status.FORBIDDEN -> Constants.Status.REASON_FORBIDDEN;
            case Constants.Status.NOT_FOUND -> Constants.Status.REASON_NOT_FOUND;
            case Constants.Status.METHOD_NOT_ALLOWED -> Constants.Status.REASON_METHOD_NOT_ALLOWED;
            case Constants.Status.PAYLOAD_TOO_LARGE -> Constants.Status.REASON_PAYLOAD_TOO_LARGE;
            default -> Constants.Status.REASON_INTERNAL_SERVER_ERROR;
        };
    }
}
