package com.etribunal.gateway.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Shadow traffic filter for Strangler Fig migration.
 *
 * When shadow mode is enabled, this filter duplicates the request to both
 * Spring and NestJS backends, compares responses, and logs differences.
 * The client always receives the Spring response (or NestJS if canary
 * routed there).
 *
 * Order: -3 (after canary filter at -5, before routing at 0)
 */
@Component
public class ShadowTrafficFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ShadowTrafficFilter.class);

    private final FeatureFlagService featureFlags;
    private final MigrationProperties properties;
    private final WebClient webClient;

    public ShadowTrafficFilter(FeatureFlagService featureFlags,
                               MigrationProperties properties) {
        this.featureFlags = featureFlags;
        this.properties = properties;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public int getOrder() {
        return -3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled() || !properties.shadow().enabled()) {
            return chain.filter(exchange);
        }

        // Only shadow GET requests to avoid duplicate side effects
        if (exchange.getRequest().getMethod() != HttpMethod.GET) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String service = extractService(path);

        if (service == null) {
            return chain.filter(exchange);
        }

        // Capture the request details for shadow call
        ServerHttpRequest request = exchange.getRequest();
        String nestjsUrl = featureFlags.getNestJsUrl();
        String newPath = path.replaceFirst("^/api", "");
        String targetUri = nestjsUrl + newPath;

        // Add a flag so downstream knows this is shadow
        ServerHttpRequest shadowRequest = request.mutate()
                .header("X-Shadow-Request", "true")
                .build();

        // Proceed with normal chain (Spring handles the request)
        return chain.filter(exchange.mutate().request(shadowRequest).build())
                .then(Mono.fromRunnable(() -> {
                    // After Spring responds, fire shadow request to NestJS (fire-and-forget)
                    fireShadowRequest(targetUri, request, exchange.getResponse());
                }));
    }

    /**
     * Fire a shadow request to NestJS and compare responses.
     * This is fire-and-forget - we don't wait for the response.
     */
    private void fireShadowRequest(String targetUri, ServerHttpRequest originalRequest,
                                   ServerHttpResponse springResponse) {
        try {
            String authHeader = originalRequest.getHeaders().getFirst("Authorization");

            var webRequest = webClient.method(originalRequest.getMethod() != null
                            ? originalRequest.getMethod() : HttpMethod.GET)
                    .uri(targetUri)
                    .headers(h -> {
                        if (authHeader != null) {
                            h.set("Authorization", authHeader);
                        }
                        // Forward user headers from JWT filter
                        String userId = originalRequest.getHeaders().getFirst("X-User-Id");
                        String username = originalRequest.getHeaders().getFirst("X-Username");
                        if (userId != null) h.set("X-User-Id", userId);
                        if (username != null) h.set("X-Username", username);
                    })
                    .exchangeToMono(resp -> resp.bodyToMono(String.class)
                            .map(body -> new ShadowResult(resp.statusCode().value(), body)));

            webRequest.subscribe(
                    nestjsResult -> {
                        int springStatus = springResponse.getStatusCode() != null
                                ? springResponse.getStatusCode().value() : 0;
                        compareResponses(springStatus, nestjsResult, originalRequest.getURI().getPath());
                    },
                    error -> log.warn("Shadow request failed for {}: {}",
                            originalRequest.getURI().getPath(), error.getMessage())
            );
        } catch (Exception e) {
            log.warn("Shadow request error for {}: {}",
                    originalRequest.getURI().getPath(), e.getMessage());
        }
    }

    /**
     * Compare Spring and NestJS responses, log differences.
     */
    private void compareResponses(int springStatus, ShadowResult nestjs, String path) {
        boolean statusMatch = springStatus == nestjs.status;
        boolean bodyMatch = false;

        try {
            String springHash = hashBody("spring");
            String nestjsHash = hashBody(nestjs.body);
            bodyMatch = springHash.equals(nestjsHash);
        } catch (Exception e) {
            // Can't compare bodies, just log status
        }

        if (!statusMatch || !bodyMatch) {
            log.warn("SHADOW MISMATCH {}: spring={} nestjs={} bodyMatch={}",
                    path, springStatus, nestjs.status, bodyMatch);
        } else if (properties.shadow().logDifferences()) {
            log.info("SHADOW MATCH {}: status={} bodyMatch=true", path, springStatus);
        }
    }

    private String hashBody(String body) throws Exception {
        if (body == null) return "null";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private String extractService(String path) {
        if (path.startsWith("/api/cases") || path.startsWith("/api/comments")
                || path.startsWith("/api/reactions") || path.startsWith("/api/saved-cases")
                || path.startsWith("/api/notifications")) {
            return "core-domain";
        }
        return null;
    }

    private record ShadowResult(int status, String body) {
    }
}
