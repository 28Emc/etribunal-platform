package com.etribunal.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Config del gateway. {@code enabled} es fail-closed: si la propiedad falta,
 * el filtro JWT permanece activo (default {@code true}) en lugar de desactivarse
 * en silencio.
 */
@ConfigurationProperties(prefix = "etribunal.gateway")
public record GatewayAuthProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue({}) List<String> publicPaths,
        @DefaultValue("false") boolean exposeActuator,
        @DefaultValue("http://localhost:3000") List<String> allowedOrigins
) {
    public GatewayAuthProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        allowedOrigins = allowedOrigins == null ? List.of("http://localhost:3000")
                : List.copyOf(allowedOrigins);
    }
}