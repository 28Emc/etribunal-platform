package com.etribunal.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales para llamadas internas service-to-service (identity-service).
 * Compartidas vía variable de entorno INTERNAL_API_KEY en ambos servicios.
 */
@ConfigurationProperties(prefix = "etribunal.internal")
public record InternalApiProperties(String identityBaseUrl, String token) {
}
