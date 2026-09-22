package com.etribunal.ai.automation.api;

import com.etribunal.common.security.JwtTokenProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.Set;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Autoriza el handshake STOMP del panel de administración ({@code /ws/automation}).
 *
 * <p>Rechaza el frame CONNECT si la sesión no presenta un access token JWT válido con rol
 * ADMIN/SYSADMIN. Al lanzar una excepción en preSend, Spring cierra la sesión antes de que
 * pueda suscribirse o enviar mensajes — cierra el hueco de {@code setAllowedOriginPatterns("*")}
 * y el {@code trigger} sin check.</p>
 */
@Component
public class AutomationWsChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SYSADMIN");
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_ROLES = "roles";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    private final JwtTokenProvider jwtTokenProvider;

    public AutomationWsChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand()) && !isAdmin(accessor)) {
            throw new MessagingException(
                    "Se requiere un access token con rol administrador para conectar");
        }
        return message;
    }

    private boolean isAdmin(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        return jwtTokenProvider.parseAccessToken(token)
                .map(this::hasAdminRole)
                .orElse(false);
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