package com.etribunal.gateway.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class ShadowTrafficFilterTest {

    private static final MigrationProperties SHADOW_ENABLED = new MigrationProperties(
            true, "http://localhost:3001/api",
            new MigrationProperties.CanaryProperties(false, 0),
            new MigrationProperties.ShadowProperties(true, false));

    private FeatureFlagService featureFlags;
    private ShadowComparer comparer;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        featureFlags = mock(FeatureFlagService.class);
        comparer = mock(ShadowComparer.class);
        chain = mock(GatewayFilterChain.class);
        when(featureFlags.getNestJsUrl()).thenReturn("http://localhost:3001/api");
    }

    @Test
    void filterSendsRealSpringBodyToComparer_preservingQueryString() {
        AtomicReference<String> nestjsUri = new AtomicReference<>();
        when(chain.filter(any())).thenAnswer(inv -> {
            ServerWebExchange exchange = inv.getArgument(0, ServerWebExchange.class);
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(payload);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        });

        ExchangeFunction nestjsFn = clientRequest -> {
            nestjsUri.set(clientRequest.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"ok\":true}")
                    .build());
        };
        WebClient webClient = WebClient.builder().exchangeFunction(nestjsFn).build();
        ShadowTrafficFilter filter =
                new ShadowTrafficFilter(featureFlags, SHADOW_ENABLED, comparer, webClient);

        var request = MockServerHttpRequest.get("/api/cases/123?page=2&take=10").build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        // El comparer recibe el body REAL de Spring (no el literal "spring") con status 200.
        verify(comparer, timeout(2000))
                .compare(eq(200), eq("{\"ok\":true}"), eq(200), eq("{\"ok\":true}"),
                        eq("/api/cases/123"), anyBoolean());
        // El shadow request conserva el query string (regresión #4 / #19).
        assertThat(nestjsUri.get()).isEqualTo("http://localhost:3001/api/cases/123?page=2&take=10");
    }

    @Test
    void filterSkipsWhenShadowDisabled() {
        MigrationProperties disabled = new MigrationProperties(
                true, "http://localhost:3001/api",
                new MigrationProperties.CanaryProperties(false, 0),
                new MigrationProperties.ShadowProperties(false, false));
        when(chain.filter(any())).thenReturn(Mono.empty());
        ShadowTrafficFilter filter = new ShadowTrafficFilter(featureFlags, disabled, comparer);

        var request = MockServerHttpRequest.get("/api/cases/1").build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verifyNoInteractions(comparer);
    }

    @Test
    void filterSkipsNonGetRequests() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        ShadowTrafficFilter filter = new ShadowTrafficFilter(featureFlags, SHADOW_ENABLED, comparer);

        var request = MockServerHttpRequest.post("/api/cases").build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verifyNoInteractions(comparer);
    }

    @Test
    void filterSkipsUnknownServices() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        ShadowTrafficFilter filter = new ShadowTrafficFilter(featureFlags, SHADOW_ENABLED, comparer);

        var request = MockServerHttpRequest.get("/api/automation/run").build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verifyNoInteractions(comparer);
    }
}