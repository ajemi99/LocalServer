package com.localserver.routing;

import com.localserver.config.RouteConfig;
import com.localserver.config.ServerConfig;
import com.localserver.handlers.StaticFileHandler;
import com.localserver.handlers.UploadHandler;
import com.localserver.http.BadRequestException;
import com.localserver.http.ErrorPages;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches a request to a configured virtual server and route, then dispatches
 * the request to the appropriate handler.
 */
public class Router {

    public static HttpResponse route(HttpRequest request, int port, List<ServerConfig> servers) {

        // 1. Which configured server answers?
        ServerConfig server = selectServer(request, port, servers);
        if (server == null) {
            return ErrorPages.get(500);
        }

        // 2. Clean the path: strip "?query" and decode %XX
        String rawPath = request.path;
        int q = rawPath.indexOf('?');
        if (q != -1) {
            rawPath = rawPath.substring(0, q);
        }

        String path;
        try {
            // "+" stays "+" in a path (it does not mean space)
            path = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Bad percent-encoding");
        }
        if (path.indexOf('\0') != -1) {
            throw new BadRequestException("Null byte in path");
        }

        // 3. Which route?
        RouteConfig route = findRoute(path, server);
        if (route == null) {
            return ErrorPages.get(404, server);
        }

        // 4. Is the method allowed?
        if (!route.methods.contains(request.method)) {
            HttpResponse r = ErrorPages.get(405, server);
            r.extraHeaders = "Allow: " + String.join(", ", route.methods) + "\r\n";
            return r;
        }

        // 5. Redirect?
        if (route.redirect != null) {
            return HttpResponse.redirect(route.redirect);
        }

        // 6. Dispatch according to the HTTP method
        String relative = Route.relative(path, route.path);
        try {
            if (request.method.equals("GET")) {
                return StaticFileHandler.handle(request, path, rawPath, relative, route, server);
            } else if (request.method.equals("POST")) {
                return UploadHandler.handlePost(request, path, relative, route, server);
            } else if (request.method.equals("DELETE")) {
                return UploadHandler.handleDelete(path, relative, route, server);
            } else {
                HttpResponse r = ErrorPages.get(405, server);
                r.extraHeaders = "Allow: " + String.join(", ", route.methods) + "\r\n";
                return r;
            }
        } catch (InvalidPathException e) {
            throw new BadRequestException("Invalid path");
        } catch (IOException e) {
            return ErrorPages.get(500, server);
        }
    }

    /**
     * Selects the configured server using the local port and the Host header.
     * Kept public because the event loop needs it to enforce the per-server body
     * limit before a full request is routed.
     */
    public static ServerConfig selectServer(HttpRequest request, int port, List<ServerConfig> servers) {

        List<ServerConfig> candidates = new ArrayList<>();
        for (ServerConfig s : servers) {
            if (s.ports.contains(port)) {
                candidates.add(s);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        String host = request.headers.get("Host");
        if (host != null) {
            int colon = host.indexOf(':');
            if (colon != -1) {
                host = host.substring(0, colon);
            }
            for (ServerConfig s : candidates) {
                if (!s.serverName.isEmpty() && s.serverName.equalsIgnoreCase(host)) {
                    return s;
                }
            }
        }

        for (ServerConfig s : candidates) {
            if (s.isDefault) {
                return s;
            }
        }
        return candidates.get(0);
    }

    // The longest route prefix wins.
    private static RouteConfig findRoute(String path, ServerConfig server) {
        RouteConfig best = null;
        for (RouteConfig r : server.routes) {
            if (Route.matches(path, r.path)
                    && (best == null || Route.prefix(r.path).length() > Route.prefix(best.path).length())) {
                best = r;
            }
        }
        return best;
    }
}
