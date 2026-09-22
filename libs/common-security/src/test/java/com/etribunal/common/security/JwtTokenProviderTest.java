package com.etribunal.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String ACCESS_SECRET = "0123456789abcdef0123456789abcdef-access";
    private static final String REFRESH_SECRET = "fedcba9876543210fedcba9876543210-refresh";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(
                    ACCESS_SECRET.getBytes(),
                    REFRESH_SECRET.getBytes(),
                    "etribunal",
                    Duration.ofMinutes(15),
                    Duration.ofDays(7));

    @Test
    void accessTokenRoundTrip() throws ParseException {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "etribunal_user", List.of("USER"));

        JWTClaimsSet claims = provider.parseAccessToken(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getStringClaim(JwtTokenProvider.CLAIM_USERNAME)).isEqualTo("etribunal_user");
        assertThat(claims.getStringClaim(JwtTokenProvider.CLAIM_TOKEN_TYPE))
                .isEqualTo(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        assertThat((List<String>) claims.getClaim("roles")).containsExactly("USER");
        assertThat(claims.getIssuer()).isEqualTo("etribunal");
    }

    @Test
    void refreshTokenRoundTripAndCrossTypeRejected() {
        UUID userId = UUID.randomUUID();
        String refresh = provider.generateRefreshToken(userId, "user");

        assertThat(provider.parseRefreshToken(refresh)).isPresent();
        assertThat(provider.parseAccessToken(refresh)).isEmpty();
    }

    @Test
    void accessTokenRejectedAsRefresh() {
        String access = provider.generateAccessToken(UUID.randomUUID(), "user", List.of("USER"));
        assertThat(provider.parseRefreshToken(access)).isEmpty();
    }

    @Test
    void tamperedTokenRejected() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "user", List.of("USER"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(provider.parseAccessToken(tampered)).isEmpty();
    }

    @Test
    void expiredTokenRejected() {
        JwtTokenProvider shortLived =
                new JwtTokenProvider(
                        ACCESS_SECRET.getBytes(),
                        REFRESH_SECRET.getBytes(),
                        "etribunal",
                        Duration.ofSeconds(-10),
                        Duration.ofDays(7));
        String token = shortLived.generateAccessToken(UUID.randomUUID(), "user", List.of("USER"));
        assertThat(shortLived.parseAccessToken(token)).isEmpty();
    }

    @Test
    void wrongIssuerOrSecretRejected() {
        JwtTokenProvider otherSecret =
                new JwtTokenProvider(
                        "another-secret-0123456789abcdef01234".getBytes(),
                        REFRESH_SECRET.getBytes(),
                        "etribunal",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        String token = otherSecret.generateAccessToken(UUID.randomUUID(), "user", List.of("USER"));
        assertThat(provider.parseAccessToken(token)).isEmpty();
    }

    @Test
    void secretsShorterThan32BytesRejected() {
        byte[] shortSecret = "short".getBytes();
        byte[] refreshSecret = REFRESH_SECRET.getBytes();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(
                        shortSecret,
                        refreshSecret,
                        "etribunal",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)));

        assertThat(exception.getMessage()).contains("JWT_ACCESS_SECRET");
    }

    @Test
    void tokenWithoutExpirationRejectedInsteadOfNpe() throws Exception {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(UUID.randomUUID().toString())
                        .issuer("etribunal")
                        .claim(JwtTokenProvider.CLAIM_TOKEN_TYPE, JwtTokenProvider.TOKEN_TYPE_ACCESS)
                        .claim(JwtTokenProvider.CLAIM_USERNAME, "user")
                        .claim("roles", List.of("USER"))
                        .jwtID(UUID.randomUUID().toString())
                        .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(ACCESS_SECRET.getBytes()));

        assertThat(provider.parseAccessToken(jwt.serialize())).isEmpty();
    }

    @Test
    void nullIssuerRejectedAtConstruction() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(
                        ACCESS_SECRET.getBytes(),
                        REFRESH_SECRET.getBytes(),
                        null,
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)));

        assertThat(exception.getMessage()).contains("issuer");
    }

    @Test
    void nullTtlRejectedAtConstruction() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(
                        ACCESS_SECRET.getBytes(),
                        REFRESH_SECRET.getBytes(),
                        "etribunal",
                        null,
                        Duration.ofDays(7)));

        assertThat(exception.getMessage()).contains("TTL");
    }
}