package com.localserver.constants;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Centralized constants for the {@code com.localserver} package.
 *
 * <p>
 * Magic values, defaults, configuration keys, protocol literals and MIME
 * types used across the server are defined here in one place so they are easy
 * to find and keep consistent. Prefer referencing these constants instead of
 * duplicating literals.
 * </p>
 */
public final class Constants {

    private Constants() {
    }

    /*
     * ========================================================================
     * HTTP / protocol
     * ========================================================================
     */
    public static final class Http {

        public static final String CRLF = "\r\n";
        public static final byte[] CRLF_BYTES = { '\r', '\n' };
        public static final byte[] CRLFCRLF_BYTES = { '\r', '\n', '\r', '\n' };

        public static final String VERSION_1_1 = "HTTP/1.1";
        public static final String VERSION_1_0 = "HTTP/1.0";

        public static final String METHOD_GET = "GET";
        public static final String METHOD_POST = "POST";
        public static final String METHOD_DELETE = "DELETE";

        public static final String DEFAULT_STATUS = "200 OK";
        public static final int DEFAULT_REDIRECT_STATUS = 301;
        public static final String CREATED_STATUS = "201 Created";
        public static final String SERVER = "LocalServer";

        public static final String HEADER_CONTENT_TYPE = "Content-Type";
        public static final String CONTENT_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";
        public static final String HEADER_CONTENT_LENGTH = "Content-Length";
        public static final String HEADER_TRANSFER_ENCODING = "Transfer-Encoding";
        public static final String HEADER_HOST = "Host";
        public static final String HEADER_LOCATION = "Location";
        public static final String HEADER_ALLOW = "Allow";
        public static final String HEADER_SET_COOKIE = "Set-Cookie";
        public static final String HEADER_CONNECTION = "Connection";
        public static final String HEADER_COOKIE = "Cookie";

        public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";
        public static final String CONTENT_TYPE_TEXT = "text/plain; charset=UTF-8";
        public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
        public static final String CONTENT_TYPE_CSS = "text/css; charset=UTF-8";
        public static final String CONTENT_TYPE_JS = "application/javascript; charset=UTF-8";
        public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

        public static final java.nio.charset.Charset UTF_8 = StandardCharsets.UTF_8;
        public static final java.nio.charset.Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

        private Http() {
        }
    }

    /*
     * ========================================================================
     * HTTP status codes and reason phrases
     * ========================================================================
     */
    public static final class Status {

        public static final int BAD_REQUEST = 400;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int METHOD_NOT_ALLOWED = 405;
        public static final int PAYLOAD_TOO_LARGE = 413;
        public static final int INTERNAL_SERVER_ERROR = 500;

        public static final String REASON_BAD_REQUEST = "Bad Request";
        public static final String REASON_FORBIDDEN = "Forbidden";
        public static final String REASON_NOT_FOUND = "Not Found";
        public static final String REASON_METHOD_NOT_ALLOWED = "Method Not Allowed";
        public static final String REASON_PAYLOAD_TOO_LARGE = "Payload Too Large";
        public static final String REASON_INTERNAL_SERVER_ERROR = "Internal Server Error";

        private static final java.util.Map<Integer, String> REDIRECT_REASONS = java.util.Map.of(
                301, "Moved Permanently",
                302, "Found",
                303, "See Other",
                307, "Temporary Redirect",
                308, "Permanent Redirect");

        /** Redirect statuses accepted in the configuration. */
        public static final Set<Long> REDIRECT_CODES = Set.of(301L, 302L, 303L, 307L, 308L);

        /** Error statuses that can be associated with a custom error page. */
        public static final Set<Integer> SUPPORTED_ERROR_CODES = Set.of(
                BAD_REQUEST, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED, PAYLOAD_TOO_LARGE,
                INTERNAL_SERVER_ERROR);

        public static boolean isRedirect(int code) {
            return REDIRECT_REASONS.containsKey(code);
        }

        public static String reasonFor(int code) {
            return REDIRECT_REASONS.getOrDefault(code, "Moved Permanently");
        }

        private Status() {
        }
    }

    /*
     * ========================================================================
     * Server / networking
     * ========================================================================
     */
    public static final class Server {

        public static final String DEFAULT_HOST = "0.0.0.0";
        public static final int DEFAULT_PORT_MIN = 1;
        public static final int DEFAULT_PORT_MAX = 65535;
        public static final long DEFAULT_CLIENT_MAX_BODY_SIZE = 1024L * 1024L;

        public static final int CONNECTION_INITIAL_SIZE = 8192;
        public static final int CONNECTION_SHRINK_ABOVE = 64 * 1024;

        public static final int MAX_HEADER_SIZE = 16 * 1024;
        public static final int SELECTOR_TIMEOUT_MS = 10 * 1000;

        private Server() {
        }
    }

    /*
     * ========================================================================
     * Configuration (JSON keys and defaults)
     * ========================================================================
     */
    public static final class Config {

        public static final int MAX_NESSTING_DEPTH = 100;

        public static final String KEY_SERVERS = "servers";
        public static final String KEY_BASE_DIR = "baseDir";
        public static final String KEY_BIND_ADDRESS = "bindAddress";
        public static final String KEY_SERVER_NAMES = "serverNames";
        /** Legacy alias for {@link #KEY_BIND_ADDRESS}. */
        public static final String KEY_HOST = "host";
        /** Legacy alias for {@link #KEY_SERVER_NAMES}. */
        public static final String KEY_SERVER_NAME = "serverName";
        public static final String KEY_DEFAULT = "default";
        public static final String KEY_CLIENT_MAX_BODY_SIZE = "clientMaxBodySize";
        public static final String KEY_PORTS = "ports";
        public static final String KEY_ERROR_PAGES = "errorPages";
        public static final String KEY_ROUTES = "routes";

        public static final String KEY_PATH = "path";
        public static final String KEY_METHODS = "methods";
        public static final String KEY_REDIRECT = "redirect";
        public static final String KEY_URL = "url";
        public static final String KEY_STATUS = "status";
        public static final int DEFAULT_REDIRECT_STATUS = 301;
        public static final String KEY_ROOT = "root";
        public static final String KEY_INDEX = "index";
        public static final String KEY_DIRECTORY_LISTING = "directoryListing";
        public static final String KEY_CGI = "cgi";

        public static final String DEFAULT_METHOD = "GET";
        public static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "DELETE");

        private Config() {
        }
    }

    /*
     * ========================================================================
     * MIME types
     * ========================================================================
     */
    public static final class Mime {

        public static final Map<String, String> TYPES = Map.ofEntries(
                Map.entry("html", "text/html; charset=UTF-8"),
                Map.entry("htm", "text/html; charset=UTF-8"),
                Map.entry("css", "text/css; charset=UTF-8"),
                Map.entry("js", "application/javascript; charset=UTF-8"),
                Map.entry("json", "application/json; charset=UTF-8"),
                Map.entry("txt", "text/plain; charset=UTF-8"),
                Map.entry("png", "image/png"),
                Map.entry("jpg", "image/jpeg"),
                Map.entry("jpeg", "image/jpeg"),
                Map.entry("gif", "image/gif"),
                Map.entry("svg", "image/svg+xml"),
                Map.entry("ico", "image/x-icon"),
                Map.entry("pdf", "application/pdf"));

        public static final String DEFAULT = "application/octet-stream";

        private Mime() {
        }
    }

    /*
     * ========================================================================
     * Command line interface
     * ========================================================================
     */
    public static final class Cli {

        public static final String DEFAULT_CONFIG = "config/server.json";
        public static final String DEFAULT_COMMAND = "start";
        public static final Set<String> COMMANDS = Set.of("start", "stop", "status");

        private Cli() {
        }
    }

    /*
     * ========================================================================
     * CGI
     * ========================================================================
     */
    public static final class Cgi {

        public static final String ENV_REQUEST_METHOD = "REQUEST_METHOD";
        public static final String ENV_SERVER_PROTOCOL = "SERVER_PROTOCOL";
        public static final String ENV_REDIRECT_STATUS = "REDIRECT_STATUS";
        public static final String ENV_QUERY_STRING = "QUERY_STRING";
        public static final String ENV_CONTENT_LENGTH = "CONTENT_LENGTH";
        public static final String ENV_CONTENT_TYPE = "CONTENT_TYPE";

        public static final String REDIRECT_STATUS_VALUE = "200";

        private Cgi() {
        }
    }

    /*
     * ========================================================================
     * Multipart
     * ========================================================================
     */
    public static final class Multipart {

        public static final int MAX_PARTS = 100;
        public static final int MAX_PART_HEADERS = 8 * 1024;
        public static final byte[] CRLF = { '\r', '\n' };
        public static final byte[] CRLFCRLF = { '\r', '\n', '\r', '\n' };
        public static final String BOUNDARY_PREFIX = "boundary=";

        private Multipart() {
        }
    }

    /*
     * ========================================================================
     * Chunked transfer encoding
     * ========================================================================
     */
    public static final class Chunked {

        public static final int MAX_LINE = 1024;
        public static final int MAX_TRAILERS = 8 * 1024;

        private Chunked() {
        }
    }

    /*
     * ========================================================================
     * Uploads
     * ========================================================================
     */
    public static final class Upload {

        public static final String DEFAULT_FILENAME = "upload.bin";
        public static final int MAX_FILENAME_LENGTH = 200;

        private Upload() {
        }
    }

    /*
     * ========================================================================
     * Shutdown
     * ========================================================================
     */
    public static final class Shutdown {

        public static final String MESSAGE = "Stopping LocalServer...";
        public static final String THREAD_NAME = "localserver-shutdown";

        private Shutdown() {
        }
    }
}
