package com.etribunal.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Emisión y validación de tokens JWT (HS256) para eTribunal.
 *
 * <p>Reglas heredadas del monolito: access 15m / refresh 7d. Los secretos SIEMPRE se inyectan por
 * configuración — no existe fallback (decisión del security audit 2026-08-20).</p>
 */
public class JwtTokenProvider {

    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_USERNAME = "username";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final byte[] accessSecret;
    private final byte[] refreshSecret;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(
            byte[] accessSecret,
            byte[] refreshSecret,
            String issuer,
            Duration accessTtl,
            Duration refreshTtl) {
        if (accessSecret == null || accessSecret.length < 32) {
            throw new IllegalArgumentException("JWT_ACCESS_SECRET debe tener >= 32 bytes");
        }
        if (refreshSecret == null || refreshSecret.length < 32) {
            throw new IllegalArgumentException("JWT_REFRESH_SECRET debe tener >= 32 bytes");
        }
        this.accessSecret = accessSecret.clone();
        this.refreshSecret = refreshSecret.clone();
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String generateAccessToken(UUID userId, String username, List<String> roles) {
        return sign(userId, username, roles, TOKEN_TYPE_ACCESS, accessTtl, accessSecret);
    }

    public String generateRefreshToken(UUID userId, String username) {
        return sign(userId, username, List.of(), TOKEN_TYPE_REFRESH, refreshTtl, refreshSecret);
    }

    /** Valida firma + issuer y devuelve los claims si el token es un access token vigente. */
    public Optional<JWTClaimsSet> parseAccessToken(String token) {
        return parse(token, TOKEN_TYPE_ACCESS, accessSecret);
    }

    /** Valida firma + issuer y devuelve los claims si el token es un refresh token vigente. */
    public Optional<JWTClaimsSet> parseRefreshToken(String token) {
        return parse(token, TOKEN_TYPE_REFRESH, refreshSecret);
    }

    private String sign(
            UUID userId, String username, List<String> roles, String type, Duration ttl, byte[] secret) {
        Instant now = Instant.now();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(userId.toString())
                        .issuer(issuer)
                        .claim(CLAIM_TOKEN_TYPE, type)
                        .claim(CLAIM_USERNAME, username)
                        .claim("roles", roles)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(ttl)))
                        .jwtID(UUID.randomUUID().toString())
                        .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private Optional<JWTClaimsSet> parse(String token, String expectedType, byte[] secret) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            boolean valid =
                    jwt.verify(new MACVerifier(secret))
                            && expectedType.equals(jwt.getJWTClaimsSet().getClaim(CLAIM_TOKEN_TYPE))
                            && issuer.equals(jwt.getJWTClaimsSet().getIssuer())
                            && new Date().before(jwt.getJWTClaimsSet().getExpirationTime());
            return valid ? Optional.of(jwt.getJWTClaimsSet()) : Optional.empty();
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }
}
