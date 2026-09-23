package com.etribunal.ai.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.settings.AutomationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AutomationWebSocketControllerTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private AutomationOrchestrator orchestrator;

    @Mock
    private AutomationSettingsService settingsService;

    @Mock
    private EngagementService engagementService;

    private AutomationWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new AutomationWebSocketController(messagingTemplate, orchestrator, settingsService, engagementService);
    }

    private StompHeaderAccessor accessorFor(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        return accessor;
    }

    private SessionConnectEvent connectEvent(String sessionId) {
        StompHeaderAccessor accessor = accessorFor(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionConnectEvent(this, message);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, org.springframework.web.socket.CloseStatus.NORMAL);
    }

    @Test
    void handleSessionConnected_addsSession() {
        controller.handleSessionConnected(connectEvent("s1"));

        StompHeaderAccessor accessor = accessorFor("s1");
        controller.subscribe(accessor);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Map.class));
    }

    @Test
    void handleSessionDisconnect_removesSession() {
        controller.handleSessionConnected(connectEvent("s1"));
        controller.handleSessionDisconnect(disconnectEvent("s1"));

        StompHeaderAccessor accessor = accessorFor("s1");
        assertThatThrownBy(() -> controller.subscribe(accessor))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("administrador");
    }

    @Test
    void subscribe_withoutSession_throws() {
        StompHeaderAccessor accessor = accessorFor("unknown");

        assertThatThrownBy(() -> controller.subscribe(accessor))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_nullAccessor_throws() {
        assertThatThrownBy(() -> controller.subscribe(null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_withSession_sendsCurrentState() {
        controller.handleSessionConnected(connectEvent("s1"));
        when(orchestrator.getRecentRuns(20)).thenReturn(List.of(Map.of("id", "r1", "status", "RUNNING", "type", "RUN_UPDATE")));
        when(orchestrator.getQueueStatus()).thenReturn(Map.of("pending", 1));
        when(settingsService.getSettings()).thenReturn(Map.of("enabled", true));
        when(engagementService.getAnalyticsSummary(10)).thenReturn(Map.of("evaluatedCases", 0));

        controller.subscribe(accessorFor("s1"));

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/automation/run"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/automation/queue"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/automation/settings"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/automation/engagement"), any(Map.class));
    }

    @Test
    void subscribe_stripType_removesTypeKey() {
        controller.handleSessionConnected(connectEvent("s1"));
        when(orchestrator.getRecentRuns(20)).thenReturn(List.of(Map.of("id", "r1", "type", "RUN_UPDATE")));
        when(orchestrator.getQueueStatus()).thenReturn(Map.of());
        when(settingsService.getSettings()).thenReturn(Map.of());
        when(engagementService.getAnalyticsSummary(10)).thenReturn(Map.of());

        controller.subscribe(accessorFor("s1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/automation/run"), captor.capture());

        // stripType removes input "type"; broadcast re-adds MSG_TYPE_RUN_UPDATE only once
        assertThat(captor.getAllValues()).isNotEmpty();
        assertThat(captor.getAllValues().get(0)).containsEntry("id", "r1");
        assertThat(captor.getAllValues().get(0)).containsEntry("type", "RUN_UPDATE");
    }

    @Test
    void triggerRun_withoutSession_throws() {
        StompHeaderAccessor accessor = accessorFor("nope");
        assertThatThrownBy(() -> controller.triggerRun(true, accessor))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void triggerRun_withSession_startsAndBroadcasts() {
        controller.handleSessionConnected(connectEvent("s1"));
        UUID runId = UUID.randomUUID();
        when(orchestrator.startRun(true))
                .thenReturn(new AutomationOrchestrator.RunResult(runId, true, "RUNNING", "/api/automation/runs/" + runId));

        controller.triggerRun(true, accessorFor("s1"));

        verify(orchestrator).startRun(true);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/automation/run"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("type", "RUN_UPDATE")
                .containsEntry("id", runId.toString())
                .containsEntry("status", "RUNNING")
                .containsEntry("dryRun", true);
    }

    @Test
    void broadcastRunUpdate_noSessions_noSend() {
        controller.broadcastRunUpdate(Map.of("id", "x"));

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void broadcastRunUpdate_withSession_sends() {
        controller.handleSessionConnected(connectEvent("s1"));

        controller.broadcastRunUpdate(Map.of("id", "x"));

        verify(messagingTemplate).convertAndSend(eq("/topic/automation/run"), any(Map.class));
    }

    @Test
    void broadcastQueueUpdate_withSession_sends() {
        controller.handleSessionConnected(connectEvent("s1"));

        controller.broadcastQueueUpdate(Map.of("pending", 5));

        verify(messagingTemplate).convertAndSend(eq("/topic/automation/queue"), any(Map.class));
    }

    @Test
    void broadcastSettingsUpdate_withSession_sends() {
        controller.handleSessionConnected(connectEvent("s1"));

        controller.broadcastSettingsUpdate(Map.of("enabled", false));

        verify(messagingTemplate).convertAndSend(eq("/topic/automation/settings"), any(Map.class));
    }

    @Test
    void broadcastEngagementUpdate_withSession_sends() {
        controller.handleSessionConnected(connectEvent("s1"));

        controller.broadcastEngagementUpdate(Map.of("averageScore", 50));

        verify(messagingTemplate).convertAndSend(eq("/topic/automation/engagement"), any(Map.class));
    }

    @Test
    void broadcast_addsTypeKey() {
        controller.handleSessionConnected(connectEvent("s1"));

        controller.broadcastRunUpdate(Map.of("id", "r2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/automation/run"), captor.capture());
        assertThat(captor.getValue()).containsEntry("type", "RUN_UPDATE");
    }
}
