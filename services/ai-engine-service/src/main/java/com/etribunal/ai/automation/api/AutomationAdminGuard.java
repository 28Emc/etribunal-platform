package com.etribunal.ai.automation.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards de administración para el motor IA.
 *
 * <p>El gateway (edge) valida el JWT e inyecta los headers {@code X-User-Id},
 * {@code X-Username} y {@code X-Roles}. Estos endpoints de administración se
 * autorizan por ROL (ADMIN/SYSADMIN), no por API key (decisión Fase 4.2).
 */
@Component
public class AutomationAdminGuard {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SYSADMIN");

    public void assertAdmin(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toSet());
        boolean authorized = roles.stream().anyMatch(ADMIN_ROLES::contains);
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requiere rol administrador");
        }
    }
}
