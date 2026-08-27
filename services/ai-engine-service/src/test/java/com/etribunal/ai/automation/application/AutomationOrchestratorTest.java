package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private UserSelector userSelector;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AutomationConfig config;

    @InjectMocks
    private AutomationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        config.setEnabled(true);
        config.setDryRun(true);
        config.setDailyCasesMin(1);
        config.setDailyCasesMax(3);
        config.setUsersPerCaseMin(5);
        config.setUsersPerCaseMax(10);
        config.setIntensityMin(30);
        config.setIntensityMax(70);
        config.setMaxInteractionsPerUserPerCaseMin(1);
        config.setMaxInteractionsPerUserPerCaseMax(3);
        config.setSchedulingIntervalMin(30);
        config.setSchedulingIntervalMax(60);
        orchestrator = new AutomationOrchestrator(
                config, runRepository, caseRepository, interactionRepository,
                caseGenerator, userSelector, jdbcTemplate
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
                    // Simulate JPA id generation
                    var field = AutomationRunEntity.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(run, fakeRunId);
                    return run;
                });
        when(runRepository.findById(fakeRunId))
                .thenReturn(Optional.of(new AutomationRunEntity()));
        when(userSelector.selectDailyPool(anyInt()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(Instant.class)))
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
    }

    @Test
    void finishRun_setsFailed_whenAllFailures() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        run.setStatus(AutomationRunStatus.RUNNING);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        orchestrator.finishRun(runId, 0, 5);

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.FAILED);
    }
}