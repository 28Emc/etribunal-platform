package com.etribunal.ai.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import com.etribunal.ai.automation.application.AutomationOrchestrator;
import com.etribunal.ai.automation.application.AutomationScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AutomationControllerTest {

    @Mock
    private AutomationOrchestrator orchestrator;

    @Mock
    private AutomationScheduler scheduler;

    @InjectMocks
    private AutomationController controller;

    @Test
    void startRun_returnsAccepted() {
        UUID runId = UUID.randomUUID();
        when(orchestrator.startRun(anyBoolean())).thenReturn(
                new AutomationOrchestrator.RunResult(runId, true, "RUNNING", "/automation/runs/" + runId)
        );

        ResponseEntity<Map<String, Object>> response = controller.startRun();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsKey("runId");
        assertThat(response.getBody().get("started")).isEqualTo(true);
        assertThat(response.getBody().get("status")).isEqualTo("RUNNING");
    }

    @Test
    void getStatus_returnsOk() {
        ResponseEntity<Map<String, Object>> response = controller.getStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody().get("status")).isEqualTo("ok");
    }

    @Test
    void getRun_returnsNotFound_whenMissing() {
        when(orchestrator.getRunStatus("missing")).thenReturn(Optional.empty());

        ResponseEntity<Object> response = controller.getRun("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getRun_returnsRun_whenFound() {
        UUID runId = UUID.randomUUID();
        Map<String, Object> runMap = Map.of("id", runId.toString(), "status", "COMPLETED");
        when(orchestrator.getRunStatus(runId.toString())).thenReturn(Optional.of(runMap));

        ResponseEntity<Object> response = controller.getRun(runId.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getQueue_returnsMap() {
        when(orchestrator.getQueueStatus()).thenReturn(Map.of("scheduled", 0L, "processing", 0L));

        ResponseEntity<Map<String, Object>> response = controller.getQueue();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("scheduled");
    }
}