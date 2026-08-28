package com.etribunal.common.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credenciales para llamadas internas service-to-service.
 * Compartidas vía variable de entorno INTERNAL_API_KEY en ambos servicios.
 */
@Component
@ConfigurationProperties(prefix = "etribunal.internal")
public record InternalApiProperties(String identityBaseUrl, String coreBaseUrl, String token) {
}