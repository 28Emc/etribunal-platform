package com.etribunal.ai.config;

import com.etribunal.common.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proveedor JWT de solo validación para el motor IA (WebSocket de administración).
 *
 * <p>El gateway valida los tokens en el edge REST e inyecta {@code X-Roles}; pero el
 * WebSocket de administración se conecta directamente al servicio (8083), así que aquí
 * se valida el access token recibido en el frame STOMP CONNECT con el mismo secreto
 * compartido ({@code JWT_ACCESS_SECRET}).</p>
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${etribunal.jwt.access-secret}") String accessSecret,
            @Value("${etribunal.jwt.issuer:etribunal}") String issuer) {
        return JwtTokenProvider.forAccessValidation(
                accessSecret.getBytes(StandardCharsets.UTF_8), issuer);
    }
}