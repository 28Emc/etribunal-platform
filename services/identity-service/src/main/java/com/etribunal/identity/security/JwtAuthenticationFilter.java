package com.etribunal.identity.security;

import com.etribunal.common.security.AuthenticatedUser;
import com.etribunal.common.security.JwtTokenProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getServletPath() excluye el context-path (/api): la comparación contra
        // /auth|/actuator debe ser sobre la ruta sin prefijo.
        String path = request.getServletPath();
        return path.startsWith("/auth/") || path.equals("/auth") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<JWTClaimsSet> claims =
                jwtTokenProvider.parseAccessToken(header.substring(7));
        if (claims.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        JWTClaimsSet set = claims.get();
        Object subject = set.getSubject();
        Object usernameClaim = set.getClaim(JwtTokenProvider.CLAIM_USERNAME);
        Object rolesClaim = set.getClaim("roles");

        // Claims malformados (sub no-UUID, roles no-lista) no deben producir 500:
        // se ignora el token y el request sigue anónimo (→ 401 en rutas protegidas).
        if (!(subject instanceof String sub) || !isUuid(sub)) {
            filterChain.doFilter(request, response);
            return;
        }
        List<String> roles =
                rolesClaim instanceof List<?> rawRoles
                        ? rawRoles.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .toList()
                        : List.of();
        String username = usernameClaim instanceof String u ? u : null;

        AuthenticatedUser principal =
                new AuthenticatedUser(UUID.fromString(sub), username, roles);
        var authorities =
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));

        filterChain.doFilter(request, response);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}