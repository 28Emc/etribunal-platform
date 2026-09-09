package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.api.AutomationWebSocketController;
import com.etribunal.ai.automation.application.CaseGenerator;
import com.etribunal.ai.automation.application.UserSelector;
import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AutomationRunEntity;
import com.etribunal.ai.automation.domain.AutomationRunStatus;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import com.etribunal.ai.automation.repository.AutomationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AutomationOrchestratorTest {

    @Mock
    private AutomationRunRepository runRepository;
    @Mock
    private AutomationCaseRepository caseRepository;
    @Mock
    private AutomationInteractionRepository interactionRepository;
    @Mock
    private CaseGenerator caseGenerator;
    @Mock
    private InteractionPlanner interactionPlanner;
    @Mock
    private InteractionExecutor interactionExecutor;
    @Mock
    private UserSelector userSelector;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private JdbcTemplate identityJdbcTemplate;
    @Mock
    private AutomationOrchestrator selfProxy;
    @Mock
    private AutomationWebSocketController wsController;

    private AutomationConfig config;

    @InjectMocks
    private AutomationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        config.setEnabled(true);
        config.setDryRun(false);
        config.setLanguage("es");
        config.setDailyCasesMin(1);
        config.setDailyCasesMax(5);
        config.setUsersPerCaseMin(3);
        config.setUsersPerCaseMax(8);
        config.setIntensityMin(30);
        config.setIntensityMax(70);
        config.setMaxInteractionsPerUserPerCaseMin(1);
        config.setMaxInteractionsPerUserPerCaseMax(3);
        config.setSchedulingIntervalMin(30);
        config.setSchedulingIntervalMax(60);

        selfProxy = mock(AutomationOrchestrator.class);
        // Use lenient stubbing to avoid UnnecessaryStubbingException
        lenient().doNothing().when(wsController).broadcastRunUpdate(any());
        lenient().doNothing().when(wsController).broadcastQueueUpdate(any());
        lenient().doNothing().when(wsController).broadcastSettingsUpdate(any());
        lenient().doNothing().when(wsController).broadcastEngagementUpdate(any());
        
        orchestrator = new AutomationOrchestrator(
                config, runRepository, caseRepository, interactionRepository,
                caseGenerator, interactionPlanner, interactionExecutor,
                userSelector, jdbcTemplate, identityJdbcTemplate, selfProxy, wsController
        );
    }

    @Test
    void startRun_createsNewRun_whenNoActiveRun() {
        UUID fakeRunId = UUID.randomUUID();
        when(runRepository.findFirstByStatusInAndCreatedAtAfter(anyList(), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(runRepository.save(any(AutomationRunEntity.class)))
                .thenAnswer(invocation -> {
                    AutomationRunEntity run = invocation.getArgument(0);
                    var field = AutomationRunEntity.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(run, fakeRunId);
                    return run;
                });
        lenient().when(runRepository.findById(fakeRunId))
                .thenReturn(Optional.of(new AutomationRunEntity()));
        lenient().when(userSelector.selectDailyPool(anyInt()))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(Instant.class)))
                .thenReturn(List.of());

        AutomationOrchestrator.RunResult result = orchestrator.startRun(false);

        assertThat(result.started()).isTrue();
        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.runId()).isEqualTo(fakeRunId);
        verify(runRepository, atLeastOnce()).save(any(AutomationRunEntity.class));
    }

    @Test
    void startRun_returnsExistingRun_whenActiveRunExists() {
        AutomationRunEntity existing = new AutomationRunEntity();
        existing.setStatus(AutomationRunStatus.RUNNING);
        existing.setStartedAt(Instant.now());

        when(runRepository.findFirstByStatusInAndCreatedAtAfter(anyList(), any(Instant.class)))
                .thenReturn(Optional.of(existing));

        AutomationOrchestrator.RunResult result = orchestrator.startRun(false);

        assertThat(result.started()).isFalse();
        assertThat(result.status()).isEqualTo("RUNNING");
    }

    @Test
    void finishRun_setsCompleted_whenNoFailures() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        run.setStatus(AutomationRunStatus.RUNNING);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        orchestrator.finishRun(runId, 5, 0);

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.COMPLETED);
        assertThat(run.getCasesCreated()).isEqualTo(5);
        assertThat(run.getCasesFailed()).isEqualTo(0);
        verify(runRepository).save(run);
    }

    @Test
    void finishRun_setsPartial_whenSomeFailures() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        run.setStatus(AutomationRunStatus.RUNNING);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        orchestrator.finishRun(runId, 3, 2);

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.PARTIAL);
        assertThat(run.getCasesCreated()).isEqualTo(3);
        assertThat(run.getCasesFailed()).isEqualTo(2);
        verify(runRepository).save(run);
    }

    @Test
    void finishRun_setsFailed_whenAllFailures() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        run.setStatus(AutomationRunStatus.RUNNING);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        orchestrator.finishRun(runId, 0, 5);

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(run.getCasesCreated()).isEqualTo(0);
        assertThat(run.getCasesFailed()).isEqualTo(5);
        verify(runRepository).save(run);
    }
}