package com.etribunal.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "etribunal.jwt")
public record JwtValidationProperties(String accessSecret, String issuer) {}
