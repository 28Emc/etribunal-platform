package com.etribunal.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "etribunal.jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl) {}
