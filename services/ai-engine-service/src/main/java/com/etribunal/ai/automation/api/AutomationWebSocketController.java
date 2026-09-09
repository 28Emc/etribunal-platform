package com.etribunal.ai.automation.api;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.settings.AutomationSettingsService;
import com.etribunal.common.security.JwtTokenProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
public class AutomationWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(AutomationWebSocketController.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_ROLES = "roles";
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SYSADMIN");

    private final SimpMessageSendingOperations messagingTemplate;
    private final AutomationOrchestrator orchestrator;
    private final AutomationSettingsService settingsService;
    private final EngagementService engagementService;
    private final JwtTokenProvider jwtTokenProvider;

    // Track connected admin sessions
    private final Set<String> connectedSessions = ConcurrentHashMap.newKeySet();

    public AutomationWebSocketController(
            SimpMessageSendingOperations messagingTemplate,
            AutomationOrchestrator orchestrator,
            AutomationSettingsService settingsService,
            EngagementService engagementService,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.messagingTemplate = messagingTemplate;
        this.orchestrator = orchestrator;
        this.settingsService = settingsService;
        this.engagementService = engagementService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (isAdminSession(accessor)) {
            connectedSessions.add(sessionId);
            log.info("Admin WebSocket connected: {}", sessionId);
        } else {
            log.warn("Non-admin WebSocket connection rejected: {}", sessionId);
        }
    }

    /**
     * Autoriza la sesión de administración:
     * 1. Header {@code X-Roles} inyectado por el gateway (despliegue con WS proxied).
     * 2. Access token JWT en el frame STOMP CONNECT ({@code Authorization: Bearer <token>}).
     */
    private boolean isAdminSession(StompHeaderAccessor accessor) {
        String roles = accessor.getFirstNativeHeader("X-Roles");
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SYSADMIN"))) {
            return true;
        }
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return jwtTokenProvider.parseAccessToken(authorization.substring(BEARER_PREFIX.length()))
                    .map(this::hasAdminRole)
                    .orElse(false);
        }
        return false;
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

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        connectedSessions.remove(sessionId);
        log.info("WebSocket disconnected: {}", sessionId);
    }

    @MessageMapping("/automation/subscribe")
    public void subscribe() {
        sendCurrentState();
    }

    @MessageMapping("/automation/trigger")
    public void triggerRun(boolean dryRun) {
        var result = orchestrator.startRun(dryRun);
        broadcast("/topic/automation/run", "RUN_UPDATE", Map.of(
            "id", result.runId().toString(),
            "status", result.status(),
            "dryRun", dryRun
        ));
    }

    /** Empuja el estado completo actual a los 4 topics del panel (estado inicial al conectar). */
    private void sendCurrentState() {
        for (Map<String, Object> run : orchestrator.getRecentRuns(20)) {
            broadcast("/topic/automation/run", "RUN_UPDATE", stripType(run));
        }
        broadcast("/topic/automation/queue", "QUEUE_UPDATE", orchestrator.getQueueStatus());
        broadcast("/topic/automation/settings", "SETTINGS_UPDATE", settingsService.getSettings());
        broadcast("/topic/automation/engagement", "ENGAGEMENT_UPDATE",
                engagementService.getAnalyticsSummary(10));
    }

    private static Map<String, Object> stripType(Map<String, Object> payload) {
        Map<String, Object> copy = new HashMap<>(payload);
        copy.remove("type");
        return copy;
    }

    // Called by orchestrator to broadcast updates
    private void broadcast(String destination, String type, Map<String, Object> payload) {
        if (connectedSessions.isEmpty()) return;
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.putAll(payload);
        messagingTemplate.convertAndSend(destination, message);
    }

    public void broadcastRunUpdate(Map<String, Object> run) {
        broadcast("/topic/automation/run", "RUN_UPDATE", run);
    }

    public void broadcastQueueUpdate(Map<String, Object> queue) {
        broadcast("/topic/automation/queue", "QUEUE_UPDATE", queue);
    }

    public void broadcastSettingsUpdate(Map<String, Object> settings) {
        broadcast("/topic/automation/settings", "SETTINGS_UPDATE", settings);
    }

    public void broadcastEngagementUpdate(Map<String, Object> engagement) {
        broadcast("/topic/automation/engagement", "ENGAGEMENT_UPDATE", engagement);
    }
}