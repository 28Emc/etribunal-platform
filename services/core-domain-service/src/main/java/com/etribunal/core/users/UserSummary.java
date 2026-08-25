package com.etribunal.core.users;

import java.util.UUID;

/**
 * Resumen mínimo de usuario devuelto por identity-service (endpoint interno).
 */
public record UserSummary(UUID id, String username, String avatarUrl, boolean anonymous) {
}
