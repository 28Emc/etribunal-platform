package com.etribunal.core.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resuelve el usuario actual desde los headers que reenvía el gateway
 * (X-User-Id / X-Username tras validar el JWT en el edge).
 */
@Component
public class CurrentUserResolver {

    public static final String HEADER_USER_ID = "X-User-Id";

    public Optional<UUID> currentUserId(jakarta.servlet.http.HttpServletRequest request) {
        String value = request.getHeader(HEADER_USER_ID);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public UUID requiredUserId(jakarta.servlet.http.HttpServletRequest request) {
        return currentUserId(request)
                .orElseThrow(() -> new IllegalStateException("Usuario no autenticado"));
    }
}
