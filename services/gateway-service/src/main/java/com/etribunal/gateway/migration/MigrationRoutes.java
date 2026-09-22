package com.etribunal.gateway.migration;

/**
 * Mapeo de rutas para Strangler Fig: extracción de servicio y ruta canaria.
 * Centralizado para evitar drift entre {@link ShadowTrafficFilter} y
 * {@link CanaryRoutingFilter}.
 */
final class MigrationRoutes {

    private static final String API_PREFIX = "/api";

    private MigrationRoutes() {
    }

    /**
     * Extrae el nombre del servicio por prefijo de path.
     * e.g. /api/cases/123/votes → "core-domain"
     */
    static String extractService(String path) {
        if (path.startsWith("/api/cases") || path.startsWith("/api/comments")
                || path.startsWith("/api/reactions") || path.startsWith("/api/saved-cases")
                || path.startsWith("/api/notifications")) {
            return "core-domain";
        }
        if (path.startsWith("/api/auth") || path.startsWith("/api/users")) {
            return "identity";
        }
        return null;
    }

    /**
     * Extrae la clave de ruta para el flag canario, con granularidad de sub-ruta.
     * e.g. /api/cases/feed → "cases-feed", /api/auth/login → "auth-login",
     * /api/cases/123/votes → "cases-123".
     */
    static String extractRoute(String path) {
        String[] parts = path.split("/");
        if (parts.length < 3) {
            return "default";
        }
        String resource = parts[2];
        if (parts.length >= 4 && !parts[3].isEmpty()) {
            return resource + "-" + parts[3];
        }
        return resource;
    }

    /**
     * Path legacy sin el prefijo /api.
     * e.g. /api/cases/feed → /cases/feed
     */
    static String nestJsPath(String path) {
        return path.startsWith(API_PREFIX) ? path.substring(API_PREFIX.length()) : path;
    }
}