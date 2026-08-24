package com.etribunal.common.security;

import java.util.List;
import java.util.UUID;

/** Usuario autenticado propagado por el gateway y los servicios downstream. */
public record AuthenticatedUser(UUID id, String username, List<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
