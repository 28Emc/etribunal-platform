package com.etribunal.ai.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.application.AutomationScheduler;
import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.infrastructure.analytics.EngagementService;
import com.etribunal.ai.automation.infrastructure.settings.AutomationSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AutomationControllerTest {

    private static final String ADMIN = "USER,ADMIN";

    @Mock
    private AutomationOrchestrator orchestrator;

    @Mock
    private AutomationScheduler scheduler;

    @Mock
    private AutomationSettingsService settingsService;

    @Mock
    private EngagementService engagementService;

    @Spy
    private AutomationConfig config = new AutomationConfig();

    @Spy
    private AutomationAdminGuard adminGuard;

    @Mock
    private AutomationWebSocketController wsController;

    @InjectMocks
    private AutomationController controller;

    @Test
    void startRun_returnsAccepted_forAdmin() {
        UUID runId = UUID.randomUUID();
        when(orchestrator.startRun(anyBoolean())).thenReturn(
                new AutomationOrchestrator.RunResult(runId, true, "RUNNING", "/automation/runs/" + runId)
        );

        ResponseEntity<Map<String, Object>> response = controller.startRun(ADMIN, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsKey("runId");
        assertThat(response.getBody()).containsEntry("started", true);
        assertThat(response.getBody()).containsEntry("status", "RUNNING");
    }

    @Test
    void startRun_acceptsDryRunParam() {
        UUID runId = UUID.randomUUID();
        when(orchestrator.startRun(true)).thenReturn(
                new AutomationOrchestrator.RunResult(runId, true, "RUNNING", "/automation/runs/" + runId)
        );

        ResponseEntity<Map<String, Object>> response = controller.startRun(ADMIN, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("started", true);
    }

    @Test
    void startRun_forbidsNonAdmin() {
        assertThatThrownBy(() -> controller.startRun("USER", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Requiere rol administrador");
    }

    @Test
    void startRun_unauthorized_whenNoRolesHeader() {
        assertThatThrownBy(() -> controller.startRun(null, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No autenticado");
    }

    @Test
    void getStatus_returnsOk() {
        ResponseEntity<Map<String, Object>> response = controller.getStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody()).containsEntry("status", "ok");
    }

    @Test
    void getRun_returnsNotFound_whenMissing() {
        when(orchestrator.getRunStatus("missing")).thenReturn(Optional.empty());

        ResponseEntity<Object> response = controller.getRun(ADMIN, "missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getRun_returnsRun_whenFound() {
        UUID runId = UUID.randomUUID();
        Map<String, Object> runMap = Map.of("id", runId.toString(), "status", "COMPLETED");
        when(orchestrator.getRunStatus(runId.toString())).thenReturn(Optional.of(runMap));

        ResponseEntity<Object> response = controller.getRun(ADMIN, runId.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getQueue_returnsMap() {
        when(orchestrator.getQueueStatus()).thenReturn(Map.of("scheduled", 0L, "processing", 0L));

        ResponseEntity<Map<String, Object>> response = controller.getQueue(ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("scheduled");
    }

    @Test
    void getSettings_returnsSettings_forAdmin() {
        when(settingsService.getSettings()).thenReturn(Map.of("enabled", true));

        ResponseEntity<Map<String, Object>> response = controller.getSettings(ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("enabled");
    }

    @Test
    void getSettings_forbidsNonAdmin() {
        assertThatThrownBy(() -> controller.getSettings("USER"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateSettings_persists_forAdmin() {
        Map<String, Object> changes = Map.of("enabled", true);
        when(settingsService.updateSettings(changes)).thenReturn(Map.of("enabled", true));

        ResponseEntity<Map<String, Object>> response = controller.updateSettings(ADMIN, changes);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(settingsService).updateSettings(changes);
    }

    @Test
    void getEngagement_returnsSummary() {
        when(engagementService.getAnalyticsSummary(3)).thenReturn(
                Map.of("evaluatedCases", 4, "averageScore", 55));

        ResponseEntity<Map<String, Object>> response = controller.getEngagement(ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("averageScore");
    }
}
