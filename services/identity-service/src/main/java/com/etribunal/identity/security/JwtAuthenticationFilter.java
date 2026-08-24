package com.etribunal.identity.security;

import com.etribunal.common.security.AuthenticatedUser;
import com.etribunal.common.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
        String path = request.getRequestURI();
        return path.startsWith("/auth/") || path.equals("/auth") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional.ofNullable(jwtTokenProvider.parseAccessToken(header.substring(7)))
                    .flatMap(claims -> claims)
                    .ifPresent(
                            claims -> {
                                AuthenticatedUser principal =
                                        new AuthenticatedUser(
                                                java.util.UUID.fromString(claims.getSubject()),
                                                (String) claims.getClaim(JwtTokenProvider.CLAIM_USERNAME),
                                                (List<String>) claims.getClaim("roles"));
                                var authorities =
                                        principal.roles().stream()
                                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                                .toList();
                                SecurityContextHolder.getContext()
                                        .setAuthentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        principal, null, authorities));
                            });
        }
        filterChain.doFilter(request, response);
    }
}
