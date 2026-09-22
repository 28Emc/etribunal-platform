package com.etribunal.ai.automation.api;

import com.etribunal.common.security.JwtTokenProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guard de administración para el motor IA.
 *
 * <p>Autorización por JWT (access token) con rol ADMIN/SYSADMIN, el MISMO modelo de
 * confianza que usa el WebSocket de administración. No se confía en headers
 * inyectables tipo {@code X-Roles}: el gateway los asigna, pero api-engine escucha
 * en red directa (8083) y cualquier origen podría fabricarlos.</p>
 */
@Component
public class AutomationAdminGuard {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SYSADMIN");
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_ROLES = "roles";

    private final JwtTokenProvider jwtTokenProvider;

    public AutomationAdminGuard(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void assertAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        JWTClaimsSet claims = jwtTokenProvider
                .parseAccessToken(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado"));
        if (!hasAdminRole(claims)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requiere rol administrador");
        }
    }

    private boolean hasAdminRole(JWTClaimsSet claims) {
        Object rolesClaim = claims.getClaim(CLAIM_ROLES);
        if (!(rolesClaim instanceof List<?> roles)) {
            return false;
        }
        for (Object role : roles) {
            if (role instanceof String s && ADMIN_ROLES.contains(s)) {
                return true;
            }
        }
        return false;
    }
}