package com.localserver.routing;

/**
 * Route matching helpers shared by the router and the request handlers.
 * Paths are normalized without touching the filesystem.
 */
public final class Route {

    private Route() {
    }

    /** Removes a trailing slash except for the root route. */
    public static String prefix(String routePath) {
        return (routePath.length() > 1 && routePath.endsWith("/"))
                ? routePath.substring(0, routePath.length() - 1)
                : routePath;
    }

    public static boolean matches(String path, String routePath) {
        String prefix = prefix(routePath);
        if (prefix.equals("/")) {
            return true;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * Returns the part of {@code path} below the route prefix, without a leading
     * slash. The root route keeps the whole path.
     */
    public static String relative(String path, String routePath) {
        String prefix = prefix(routePath);
        String relative = prefix.equals("/") ? path : path.substring(prefix.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
    }
}
