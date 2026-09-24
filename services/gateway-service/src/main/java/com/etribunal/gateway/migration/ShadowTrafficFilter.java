package com.etribunal.gateway.migration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Shadow traffic filter for Strangler Fig migration.
 *
 * <p>When shadow mode is enabled, this filter duplicates the request to both
 * Spring and NestJS backends, compares the <b>real</b> Spring response body with
 * the NestJS one, and logs differences. The client always receives the Spring
 * response (or NestJS if canary routed there).</p>
 *
 * <p>Order: -3 (after canary filter at -5, before routing at 0)</p>
 */
@Component
public class ShadowTrafficFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ShadowTrafficFilter.class);
    private static final Duration SHADOW_TIMEOUT = Duration.ofSeconds(5);

    private final FeatureFlagService featureFlags;
    private final MigrationProperties properties;
    private final ShadowComparer comparer;
    private final WebClient webClient;

    @Autowired
    public ShadowTrafficFilter(FeatureFlagService featureFlags,
                               MigrationProperties properties,
                               ShadowComparer comparer) {
        this(featureFlags, properties, comparer, WebClient.builder().build());
    }

    ShadowTrafficFilter(FeatureFlagService featureFlags,
                        MigrationProperties properties,
                        ShadowComparer comparer,
                        WebClient webClient) {
        this.featureFlags = featureFlags;
        this.properties = properties;
        this.comparer = comparer;
        this.webClient = webClient;
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
        if (MigrationRoutes.extractService(path) == null) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String targetUri = targetUri(request);

        // Add a flag so downstream knows this is shadow
        ServerHttpRequest shadowRequest = request.mutate()
                .header("X-Shadow-Request", "true")
                .build();

        // Capture the real Spring response body while it flows to the client.
        ShadowResponseDecorator capturedResponse = new ShadowResponseDecorator(exchange.getResponse());

        // Proceed with normal chain (Spring handles the request)
        return chain.filter(exchange.mutate().request(shadowRequest).response(capturedResponse).build())
                .then(Mono.fromRunnable(() ->
                        fireShadowRequest(targetUri, request, capturedResponse)));
    }

    private String targetUri(ServerHttpRequest request) {
        String nestjsUrl = featureFlags.getNestJsUrl();
        String newPath = MigrationRoutes.nestJsPath(request.getURI().getPath());
        String query = request.getURI().getRawQuery();
        return nestjsUrl + newPath + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    /**
     * Fire a shadow request to NestJS and compare responses.
     * This is fire-and-forget - we don't wait for the response.
     */
    private void fireShadowRequest(String targetUri, ServerHttpRequest originalRequest,
                                   ShadowResponseDecorator springResponse) {
        HttpMethod method = originalRequest.getMethod();

        String authHeader = originalRequest.getHeaders().getFirst("Authorization");

        var webRequest = webClient.method(method)
                .uri(targetUri)
                .headers(h -> {
                    if (authHeader != null) {
                        h.set("Authorization", authHeader);
                    }
                    String userId = originalRequest.getHeaders().getFirst("X-User-Id");
                    String username = originalRequest.getHeaders().getFirst("X-Username");
                    if (userId != null) h.set("X-User-Id", userId);
                    if (username != null) h.set("X-Username", username);
                })
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> new ShadowResult(resp.statusCode().value(), body)))
                .timeout(SHADOW_TIMEOUT);

        webRequest.subscribe(
                nestjsResult -> comparer.compare(
                        springResponse.capturedStatusCode(),
                        springResponse.bodyString(),
                        nestjsResult.status,
                        nestjsResult.body,
                        originalRequest.getURI().getPath(),
                        properties.shadow().logDifferences()),
                error -> log.warn("Shadow request failed for {}: {}",
                        originalRequest.getURI().getPath(), error.getMessage())
        );
    }

    private record ShadowResult(int status, String body) {
    }

    /**
     * Decorador que captura el body real enviado al cliente para poder
     * compararlo con la respuesta de NestJS.
     */
    private static final class ShadowResponseDecorator extends ServerHttpResponseDecorator {

        private final ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
        private final AtomicInteger capturedStatus = new AtomicInteger(0);

        private ShadowResponseDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }

        int capturedStatusCode() {
            HttpStatusCode delegateStatusCode = getDelegate().getStatusCode();
            int delegateStatus = 0;
            if (delegateStatusCode != null) {
                delegateStatus = delegateStatusCode.value();
            }
            return capturedStatus.get() != 0 ? capturedStatus.get() : delegateStatus;
        }

        String bodyString() {
            return bodyBuffer.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
            HttpStatusCode delegateStatusCode = getDelegate().getStatusCode();
            if (delegateStatusCode != null) {
                capturedStatus.set(delegateStatusCode.value());
            }
            return DataBufferUtils.join(Flux.from(body))
                    .flatMap(joined -> {
                        byte[] bytes = new byte[joined.readableByteCount()];
                        joined.read(bytes);
                        try {
                            bodyBuffer.write(bytes);
                        } catch (IOException e) {
                            // best-effort capture
                        }
                        DataBuffer wrapped = DefaultDataBufferFactory.sharedInstance.wrap(bytes);
                        return super.writeWith(Mono.just(wrapped));
                    })
                    .onErrorResume(t -> super.writeWith(body));
        }
    }
}