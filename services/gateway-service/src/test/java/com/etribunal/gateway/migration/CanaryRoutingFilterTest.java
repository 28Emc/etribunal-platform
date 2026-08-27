package com.etribunal.gateway.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CanaryRoutingFilterTest {

    @Mock
    private FeatureFlagService featureFlags;

    @Mock
    private GatewayFilterChain chain;

    private CanaryRoutingFilter filter;

    private static final MigrationProperties ENABLED_PROPS = new MigrationProperties(
            true, "http://localhost:3001/api",
            new MigrationProperties.CanaryProperties(true, 0),
            new MigrationProperties.ShadowProperties(false, false));

    private static final MigrationProperties DISABLED_PROPS = new MigrationProperties(
            false, "http://localhost:3001/api",
            new MigrationProperties.CanaryProperties(true, 0),
            new MigrationProperties.ShadowProperties(false, false));

    @BeforeEach
    void setUp() {
        lenient().when(chain.filter(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void filterSkipsWhenMigrationDisabled() {
        filter = new CanaryRoutingFilter(featureFlags, DISABLED_PROPS);
        ServerWebExchange exchange = createExchange("/api/cases");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filterRoutesToSpringWhenCanarySaysYes() {
        filter = new CanaryRoutingFilter(featureFlags, ENABLED_PROPS);
        when(featureFlags.shouldRouteToSpring("core-domain", "cases"))
                .thenReturn(Mono.just(true));

        ServerWebExchange exchange = createExchange("/api/cases");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Should pass through to chain without rewriting URI
        verify(chain).filter(exchange);
    }

    @Test
    void filterRoutesToNestJsWhenCanarySaysNo() {
        filter = new CanaryRoutingFilter(featureFlags, ENABLED_PROPS);
        when(featureFlags.shouldRouteToSpring("core-domain", "cases"))
                .thenReturn(Mono.just(false));
        when(featureFlags.getNestJsUrl()).thenReturn("http://localhost:3001/api");

        ServerWebExchange exchange = createExchange("/api/cases");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Should rewrite URI to NestJS
        verify(chain).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void filterSkipsForUnknownService() {
        filter = new CanaryRoutingFilter(featureFlags, ENABLED_PROPS);

        ServerWebExchange exchange = createExchange("/api/automation/run");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Unknown service, should pass through
        verify(chain).filter(exchange);
    }

    @Test
    void filterHandlesAuthPaths() {
        filter = new CanaryRoutingFilter(featureFlags, ENABLED_PROPS);
        when(featureFlags.shouldRouteToSpring("identity", "auth"))
                .thenReturn(Mono.just(false));
        when(featureFlags.getNestJsUrl()).thenReturn("http://localhost:3001/api");

        ServerWebExchange exchange = createExchange("/api/auth/login");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(org.mockito.ArgumentMatchers.any());
    }

    private ServerWebExchange createExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .build();
        return MockServerWebExchange.from(request);
    }
}
