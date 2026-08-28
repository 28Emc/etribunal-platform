package com.etribunal.gateway.security;

import com.etribunal.common.security.JwtTokenProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_ROLES = "X-Roles";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_ROLES = "roles";

    private final JwtTokenProvider jwtTokenProvider;
    private final GatewayAuthProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtGatewayFilter(JwtTokenProvider jwtTokenProvider, GatewayAuthProperties properties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        if (isPublic(request.getURI().getPath())) {
            // Auth opcional: anónimo pasa sin headers; con token válido se adjunta la identidad
            Optional<ServerHttpRequest> mutated = tryAttachIdentity(request);
            return mutated
                    .map(m -> chain.filter(exchange.mutate().request(m).build()))
                    .orElseGet(() -> chain.filter(exchange));
        }

        Optional<ServerHttpRequest> mutated = tryAttachIdentity(request);
        if (mutated.isEmpty()) {
            return unauthorized(exchange);
        }
        return chain.filter(exchange.mutate().request(mutated.get()).build());
    }

    private Optional<ServerHttpRequest> tryAttachIdentity(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        var claims =
                jwtTokenProvider.parseAccessToken(authorization.substring(BEARER_PREFIX.length()));
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        var c = claims.get();
        @SuppressWarnings("unchecked")
        List<String> roles =
                c.getClaim(CLAIM_ROLES) instanceof List
                        ? (List<String>) c.getClaim(CLAIM_ROLES)
                        : List.<String>of();
        return Optional.of(
                request.mutate()
                        .header(HEADER_USER_ID, c.getSubject())
                        .header(
                                HEADER_USERNAME,
                                String.valueOf(c.getClaim(JwtTokenProvider.CLAIM_USERNAME)))
                        .header(
                                HEADER_ROLES,
                                roles.isEmpty() ? "" : String.join(",", roles))
                        .build());
    }

    boolean isPublic(String path) {
        return path.startsWith("/actuator")
                || properties.publicPaths().stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
