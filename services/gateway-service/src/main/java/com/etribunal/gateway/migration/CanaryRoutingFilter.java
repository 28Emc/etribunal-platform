package com.etribunal.gateway.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Canary routing filter for Strangler Fig migration.
 *
 * <p>When canary is enabled, this filter checks the feature flag for the matched
 * route. If the flag says "route to Spring", the request proceeds normally
 * (already routed by Spring Cloud Gateway). If the flag says "route to NestJS",
 * the filter rewrites the URI to point to the legacy backend, preservando el
 * query string (page/take/q).</p>
 *
 * <p>Order: -5 (after JWT filter at -10, before routing at 0)</p>
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
        String service = MigrationRoutes.extractService(path);
        String route = MigrationRoutes.extractRoute(path);

        if (service == null) {
            return chain.filter(exchange);
        }

        return featureFlags.shouldRouteToSpring(service, route)
                .flatMap(shouldSpring -> {
                    if (shouldSpring != null && shouldSpring) {
                        // Route to Spring (default behavior)
                        log.debug("Canary → Spring: {} {}", service, path);
                        return chain.filter(exchange);
                    } else {
                        // Route to NestJS (legacy)
                        String nestjsUrl = featureFlags.getNestJsUrl();
                        String newPath = MigrationRoutes.nestJsPath(path);
                        String query = exchange.getRequest().getURI().getRawQuery();
                        String targetUri =
                                nestjsUrl + newPath + (query == null || query.isEmpty() ? "" : "?" + query);

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
}
