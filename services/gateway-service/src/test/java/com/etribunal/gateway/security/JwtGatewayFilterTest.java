package com.etribunal.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class JwtGatewayFilterTest {

    private static final String ACCESS_SECRET = "dev-only-access-secret-0123456789abcdef0123456789abcdef";
    private static final String BEARER_PREFIX_VALUE = "Bearer ";

    private JwtGatewayFilter filter;
    private GatewayFilterChain chain;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                ACCESS_SECRET.getBytes(StandardCharsets.UTF_8),
                "dev-only-refresh-secret-fedcba9876543210fedcba9876543210".getBytes(),
                "etribunal",
                Duration.ofMinutes(15),
                Duration.ofDays(7));
        var props =
                new GatewayAuthProperties(
                        true, List.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh"));
        filter = new JwtGatewayFilter(provider, props);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private MockServerWebExchange exchange(String method, String path, String bearer) {
        var builder = MockServerHttpRequest.method(method, path);
        if (bearer != null) {
            builder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX_VALUE + bearer);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void publicPathWithoutTokenPassesThrough() {
        var exchange = exchange("POST", "/api/auth/login", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuatorIsAlwaysPublic() {
        var exchange = exchange("GET", "/actuator/health", null);

        assertThat(filter.isPublic("/actuator/health")).isTrue();
        filter.filter(exchange, chain).block();
        verify(chain).filter(any());
    }

    @Test
    void protectedPathWithoutTokenRejected401() {
        var exchange = exchange("GET", "/api/users/me", null);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedPathWithGarbageTokenRejected401() {
        var exchange = exchange("GET", "/api/users/me", "not.a.jwt");

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshTokenRejectedAtEdge() {
        String refresh = provider.generateRefreshToken(UUID.randomUUID(), "user");
        var exchange = exchange("GET", "/api/users/me", refresh);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validAccessTokenForwardsIdentityHeaders() {
        UUID userId = UUID.randomUUID();
        String access = provider.generateAccessToken(userId, "ana_t", List.of("USER"));
        var exchange = exchange("GET", "/api/users/me", access);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        var forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst(JwtGatewayFilter.HEADER_USER_ID)).isEqualTo(userId.toString());
        assertThat(forwarded.getFirst(JwtGatewayFilter.HEADER_USERNAME)).isEqualTo("ana_t");
        assertThat(forwarded.getFirst(JwtGatewayFilter.HEADER_ROLES)).isEqualTo("USER");
        assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(BEARER_PREFIX_VALUE + access);
    }

    @Test
    void disabledFilterLetsEverythingThrough() {
        var disabledProps = new GatewayAuthProperties(false, List.of());
        var disabledFilter = new JwtGatewayFilter(provider, disabledProps);
        var exchange = exchange("GET", "/api/users/me", null);

        disabledFilter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }
}
