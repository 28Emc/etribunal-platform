package com.etribunal.ai.automation.api;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.settings.AutomationSettingsService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
public class AutomationWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(AutomationWebSocketController.class);
    private static final String TOPIC_RUN = "/topic/automation/run";
    private static final String MSG_TYPE_RUN_UPDATE = "RUN_UPDATE";

    private final SimpMessageSendingOperations messagingTemplate;
    private final AutomationOrchestrator orchestrator;
    private final AutomationSettingsService settingsService;
    private final EngagementService engagementService;

    // Track connected admin sessions (solo sesiones con CONNECT autenticado por JWT)
    private final Set<String> connectedSessions = ConcurrentHashMap.newKeySet();

    public AutomationWebSocketController(
            SimpMessageSendingOperations messagingTemplate,
            AutomationOrchestrator orchestrator,
            AutomationSettingsService settingsService,
            EngagementService engagementService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.orchestrator = orchestrator;
        this.settingsService = settingsService;
        this.engagementService = engagementService;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        connectedSessions.add(sessionId);
        log.info("Admin WebSocket connected: {}", sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        connectedSessions.remove(sessionId);
        log.info("WebSocket disconnected: {}", sessionId);
    }

    @MessageMapping("/automation/subscribe")
    public void subscribe(StompHeaderAccessor accessor) {
        assertAdminSession(accessor);
        sendCurrentState();
    }

    @MessageMapping("/automation/trigger")
    public void triggerRun(boolean dryRun, StompHeaderAccessor accessor) {
        assertAdminSession(accessor);
        var result = orchestrator.startRun(dryRun);
        broadcast(TOPIC_RUN, MSG_TYPE_RUN_UPDATE, Map.of(
            "id", result.runId().toString(),
            "status", result.status(),
            "dryRun", dryRun
        ));
    }

    private void assertAdminSession(StompHeaderAccessor accessor) {
        if (accessor == null || !connectedSessions.contains(accessor.getSessionId())) {
            throw new MessagingException("Se requiere rol administrador");
        }
    }

    /** Empuja el estado completo actual a los 4 topics del panel (estado inicial al conectar). */
    private void sendCurrentState() {
        for (Map<String, Object> run : orchestrator.getRecentRuns(20)) {
            broadcast(TOPIC_RUN, MSG_TYPE_RUN_UPDATE, stripType(run));
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
        broadcast(TOPIC_RUN, MSG_TYPE_RUN_UPDATE, run);
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