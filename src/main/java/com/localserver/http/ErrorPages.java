package com.localserver.http;

import com.localserver.config.ServerConfig;
import com.localserver.constants.Constants;

import java.nio.file.Files;
import java.nio.file.Path;

public class ErrorPages {

    public static HttpResponse get(int code) {
        int normalized = HttpStatus.normalize(code);
        String message = HttpStatus.reason(normalized);

        String html = "<!DOCTYPE html>\n"
                + "<html><head><meta charset=\"UTF-8\"><title>" + normalized + " " + message + "</title></head>\n"
                + "<body style=\"font-family:sans-serif;text-align:center;margin-top:15%\">\n"
                + "<h1>" + normalized + "</h1>\n"
                + "<p>" + message + "</p>\n"
                + "<hr><small>LocalServer</small>\n"
                + "</body></html>\n";

        return new HttpResponse(normalized + " " + message, Constants.Http.CONTENT_TYPE_HTML, html);
    }

    /**
     * Returns the configured custom error page when it exists, otherwise the
     * built-in page. Relative paths are resolved against the server base
     * directory rather than the process working directory.
     */
    public static HttpResponse get(int code, ServerConfig server) {
        HttpResponse fallback = get(code);
        String file = server.errorPages.get(code);
        if (file != null) {
            try {
                Path path = server.resolvePath(file);
                if (Files.isRegularFile(path)) {
                    return new HttpResponse(fallback.status, Constants.Http.CONTENT_TYPE_HTML,
                            Files.readAllBytes(path));
                }
            } catch (Exception e) {
                System.err.println("Cannot load error page " + code + " (" + file + "): " + e.getMessage());
            }
        }
        return fallback;
    }
}
