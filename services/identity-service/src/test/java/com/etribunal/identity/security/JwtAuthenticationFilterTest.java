package com.etribunal.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.security.JwtTokenProvider;
import com.etribunal.identity.config.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    JwtAuthenticationFilter filter;
    JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(
                "0123456789abcdef0123456789abcdef-access",
                "fedcba9876543210fedcba9876543210-refresh",
                "etribunal",
                Duration.ofMinutes(15),
                Duration.ofDays(7));
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth", "/actuator/health"})
    void shouldNotFilterExcludedPaths(String path) {
        when(request.getServletPath()).thenReturn(path);

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldFilterApiPaths() {
        when(request.getServletPath()).thenReturn("/api/users/me");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilterInternalNoAuthHeaderContinuesChain() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalInvalidBearerPrefixContinuesChain() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalInvalidTokenContinuesChain() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid");
        when(jwtTokenProvider.parseAccessToken("invalid")).thenReturn(java.util.Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void doFilterInternalMalformedSubjectContinuesChain() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        com.nimbusds.jwt.JWTClaimsSet claims = mock(com.nimbusds.jwt.JWTClaimsSet.class);
        lenient().when(claims.getSubject()).thenReturn("not-a-uuid");
        when(jwtTokenProvider.parseAccessToken("token")).thenReturn(java.util.Optional.of(claims));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalValidTokenSetsAuthentication() throws ServletException, IOException, java.text.ParseException {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

        com.nimbusds.jwt.JWTClaimsSet claims = mock(com.nimbusds.jwt.JWTClaimsSet.class);
        lenient().when(claims.getSubject()).thenReturn(userId.toString());
        lenient().when(claims.getStringClaim("username")).thenReturn("testuser");
        lenient().when(claims.getClaim("roles")).thenReturn(java.util.List.of("USER"));
        lenient().when(claims.getClaim("username")).thenReturn("testuser");

        when(jwtTokenProvider.parseAccessToken("valid-token")).thenReturn(java.util.Optional.of(claims));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(jakarta.servlet.ServletRequest.class), any(jakarta.servlet.ServletResponse.class));
    }
}