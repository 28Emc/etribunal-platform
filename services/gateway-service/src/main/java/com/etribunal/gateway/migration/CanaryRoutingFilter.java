package com.etribunal.gateway.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Canary routing filter for Strangler Fig migration.
 *
 * When canary is enabled, this filter checks the feature flag for the matched
 * route. If the flag says "route to Spring", the request proceeds normally
 * (already routed by Spring Cloud Gateway). If the flag says "route to NestJS",
 * the filter rewrites the URI to point to the legacy backend.
 *
 * Order: -5 (after JWT filter at -10, before routing at 0)
 */
@Component
public class CanaryRoutingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CanaryRoutingFilter.class);

    private final FeatureFlagService featureFlags;
    private final MigrationProperties properties;

    public CanaryRoutingFilter(FeatureFlagService featureFlags,
                               MigrationProperties properties) {
        this.featureFlags = featureFlags;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return -5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled() || !properties.canary().enabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String service = extractService(path);
        String route = extractRoute(path);

        if (service == null) {
            return chain.filter(exchange);
        }

        return featureFlags.shouldRouteToSpring(service, route)
                .flatMap(shouldSpring -> {
                    if (shouldSpring) {
                        // Route to Spring (default behavior)
                        log.debug("Canary → Spring: {} {}", service, path);
                        return chain.filter(exchange);
                    } else {
                        // Route to NestJS (legacy)
                        String nestjsUrl = featureFlags.getNestJsUrl();
                        String newPath = path.replaceFirst("^/api", "");
                        String targetUri = nestjsUrl + newPath;

                        log.debug("Canary → NestJS: {} {} → {}", service, path, targetUri);

                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .uri(java.net.URI.create(targetUri))
                                .build();

                        return chain.filter(exchange.mutate()
                                .request(mutatedRequest)
                                .build());
                    }
                });
    }

    /**
     * Extract the service name from the path.
     * e.g., /api/cases/123/votes → "core-domain"
     */
    private String extractService(String path) {
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
     * Extract a route key from the path for flag lookup.
     * e.g., /api/cases/feed → "cases-feed"
     */
    private String extractRoute(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2]; // e.g., "cases", "comments", "auth"
        }
        return "default";
    }
}
