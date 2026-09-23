package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.api.AutomationWebSocketController;
import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AutomationCaseEntity;
import com.etribunal.ai.automation.domain.AutomationCaseStatus;
import com.etribunal.ai.automation.domain.AutomationInteractionEntity;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.ai.automation.domain.AutomationRunEntity;
import com.etribunal.ai.automation.domain.AutomationRunStatus;
import com.etribunal.ai.automation.domain.AutomationInteractionStatus;
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
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(run.getCasesFailed()).isZero();
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
        assertThat(run.getCasesCreated()).isZero();
        assertThat(run.getCasesFailed()).isEqualTo(5);
        verify(runRepository).save(run);
    }

    @Test
    void resumeStaleRuns_marksStaleRunAsFailed() {
        UUID runId = UUID.randomUUID();
        Instant dayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        AutomationRunEntity staleRun = new AutomationRunEntity();
        setId(staleRun, runId);
        staleRun.setStatus(AutomationRunStatus.RUNNING);
        staleRun.setStartedAt(Instant.now().minusMillis(31 * 60 * 1000)); // older than 30 min

        when(runRepository.findActiveRunsSince(anyList(), eq(dayStart)))
                .thenReturn(List.of(staleRun));

        orchestrator.resumeStaleRuns();

        assertThat(staleRun.getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(staleRun.getErrorMessage()).isEqualTo("Stale recovery");
        verify(runRepository).save(staleRun);
        verify(wsController).broadcastRunUpdate(any(Map.class));
    }

    @Test
    void resumeStaleRuns_skipsNonStaleRun() {
        UUID runId = UUID.randomUUID();
        Instant dayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        AutomationRunEntity freshRun = new AutomationRunEntity();
        setId(freshRun, runId);
        freshRun.setStatus(AutomationRunStatus.RUNNING);
        freshRun.setStartedAt(Instant.now().minusMillis(10 * 60 * 1000)); // 10 min old

        when(runRepository.findActiveRunsSince(anyList(), eq(dayStart)))
                .thenReturn(List.of(freshRun));

        orchestrator.resumeStaleRuns();

        assertThat(freshRun.getStatus()).isEqualTo(AutomationRunStatus.RUNNING);
        verify(runRepository, never()).save(any());
    }

    @Test
    void resumeStaleRuns_skipsPendingRun() {
        Instant dayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        AutomationRunEntity pendingRun = new AutomationRunEntity();
        pendingRun.setStatus(AutomationRunStatus.PENDING);

        when(runRepository.findActiveRunsSince(anyList(), eq(dayStart)))
                .thenReturn(List.of(pendingRun));

        orchestrator.resumeStaleRuns();

        assertThat(pendingRun.getStatus()).isEqualTo(AutomationRunStatus.PENDING);
        verify(runRepository, never()).save(any());
    }

    @Test
    void getQueueStatus_returnsCounts() {
        Instant dayStart = Instant.now().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        when(interactionRepository.countByStatus(AutomationInteractionStatus.SCHEDULED)).thenReturn(5L);
        when(interactionRepository.countByStatus(AutomationInteractionStatus.PROCESSING)).thenReturn(2L);
        when(interactionRepository.countByStatusAndExecutedAtGreaterThanEqual(
                AutomationInteractionStatus.SUCCESS, dayStart)).thenReturn(10L);
        when(interactionRepository.countByStatusAndExecutedAtGreaterThanEqual(
                AutomationInteractionStatus.FAILED, dayStart)).thenReturn(1L);

        Map<String, Object> status = orchestrator.getQueueStatus();

        assertThat(status)
                .containsEntry("scheduled", 5L)
                .containsEntry("processing", 2L)
                .containsEntry("completedToday", 10L)
                .containsEntry("failedToday", 1L);
    }

    @Test
    void getRecentTopics_returnsEmptyOnError() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Instant.class)))
                .thenThrow(new RuntimeException("db down"));

        Object result = invokePrivate(orchestrator, "getRecentTopics");

        assertThat(result).isEqualTo(List.of());
    }

    @Test
    void autoEnableBots_updatesUsersInIdentityDb() throws Exception {
        when(identityJdbcTemplate.update(anyString())).thenReturn(15);

        invokePrivate(orchestrator, "autoEnableBots");

        verify(identityJdbcTemplate).update(anyString());
    }

    @Test
    void getRunStatus_returnsStatus_whenValidUuid() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        setId(run, runId);
        run.setStatus(AutomationRunStatus.RUNNING);
        run.setDryRun(true);
        run.setCasesRequested(5);
        run.setCasesCreated(3);
        run.setCasesFailed(1);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(null);
        run.setErrorMessage(null);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        Optional<Map<String, Object>> result = orchestrator.getRunStatus(runId.toString());

        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("id", runId.toString());
        assertThat(result.get()).containsEntry("status", "RUNNING");
        assertThat(result.get()).containsEntry("dryRun", true);
        assertThat(result.get()).containsEntry("casesRequested", 5);
    }

    @Test
    void getRunStatus_returnsEmpty_whenInvalidUuid() {
        Optional<Map<String, Object>> result = orchestrator.getRunStatus("not-a-uuid");

        assertThat(result).isEmpty();
    }

    @Test
    void getRunStatus_returnsEmpty_whenRunNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        Optional<Map<String, Object>> result = orchestrator.getRunStatus(runId.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void getRecentRuns_returnsLimitedRuns() {
        AutomationRunEntity run1 = new AutomationRunEntity();
        setId(run1, UUID.randomUUID());
        run1.setStatus(AutomationRunStatus.COMPLETED);
        run1.setDryRun(false);
        run1.setCasesRequested(5);
        run1.setCasesCreated(5);
        run1.setCasesFailed(0);
        run1.setStartedAt(Instant.now());
        run1.setFinishedAt(Instant.now());
        run1.setErrorMessage(null);

        AutomationRunEntity run2 = new AutomationRunEntity();
        setId(run2, UUID.randomUUID());
        run2.setStatus(AutomationRunStatus.FAILED);
        run2.setDryRun(true);
        run2.setCasesRequested(2);
        run2.setCasesCreated(0);
        run2.setCasesFailed(2);
        run2.setStartedAt(Instant.now());
        run2.setFinishedAt(Instant.now());
        run2.setErrorMessage("Test error");

        when(runRepository.findRecentRuns()).thenReturn(List.of(run1, run2));

        List<Map<String, Object>> runs = orchestrator.getRecentRuns(10);

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0)).containsEntry("id", getId(run1).toString());
        assertThat(runs.get(1)).containsEntry("status", "FAILED");
    }

    private void setId(AutomationRunEntity entity, UUID id) {
        try {
            var field = AutomationRunEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object invokePrivate(Object target, String methodName) throws Exception {
        var method = AutomationOrchestrator.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private UUID getId(AutomationRunEntity entity) {
        try {
            var field = AutomationRunEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            return (UUID) field.get(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void getRecentRuns_clampsLimit() {
        when(runRepository.findRecentRuns()).thenReturn(List.of());

        List<Map<String, Object>> runs = orchestrator.getRecentRuns(200); // > 100

        assertThat(runs).isEmpty();
        verify(runRepository).findRecentRuns();
    }

    @Test
    void startRun_usesDryRunOverride() {
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

        // Dry run override when config.dryRun=false
        AutomationOrchestrator.RunResult result = orchestrator.startRun(true);

        assertThat(result.started()).isTrue();
        assertThat(result.status()).isEqualTo("RUNNING");
    }

    @Test
    void startRun_throwsWhenDisabled() {
        config.setEnabled(false);

        assertThatThrownBy(() -> orchestrator.startRun(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Automation is disabled");
    }

    @Test
    void startRun_marksStaleActiveRunAsFailedAndCreatesNew() {
        UUID fakeRunId = UUID.randomUUID();
        AutomationRunEntity staleRun = new AutomationRunEntity();
        setId(staleRun, UUID.randomUUID());
        staleRun.setStatus(AutomationRunStatus.RUNNING);
        staleRun.setStartedAt(Instant.now().minusMillis(31 * 60 * 1000)); // > 30 min

        when(runRepository.findFirstByStatusInAndCreatedAtAfter(anyList(), any(Instant.class)))
                .thenReturn(Optional.of(staleRun));
        when(runRepository.save(any(AutomationRunEntity.class)))
                .thenAnswer(invocation -> {
                    AutomationRunEntity run = invocation.getArgument(0);
                    if (run.getId() == null) {
                        var field = AutomationRunEntity.class.getDeclaredField("id");
                        field.setAccessible(true);
                        field.set(run, fakeRunId);
                    }
                    return run;
                });

        AutomationOrchestrator.RunResult result = orchestrator.startRun(false);

        assertThat(staleRun.getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(staleRun.getErrorMessage()).isEqualTo("Stale recovery");
        assertThat(result.started()).isTrue();
        assertThat(result.runId()).isEqualTo(fakeRunId);
        verify(runRepository, atLeast(2)).save(any(AutomationRunEntity.class));
    }

    @Test
    void launchRun_completesAndSchedulesInteractions() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        setId(run, runId);
        run.setStatus(AutomationRunStatus.PENDING);

        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(userSelector.selectDailyPool(anyInt())).thenReturn(pool);
        lenient().when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(Instant.class)))
                .thenReturn(new java.util.ArrayList<>());

        com.etribunal.ai.automation.domain.dtos.GeneratedCase generated =
                new com.etribunal.ai.automation.domain.dtos.GeneratedCase(
                        "Title", "Desc", "Side A", "Side B",
                        "politica", "classic", "Sub A", "Sub B", "Both", Map.of());
        when(caseGenerator.generateCase(any(), anyInt(), any(), any(), anyBoolean()))
                .thenReturn(Mono.just(new CaseGenerator.CaseResult("case-1", AutomationCaseStatus.CREATED,
                        "u1", null, generated)));

        AutomationCaseEntity automationCase = new AutomationCaseEntity();
        when(caseRepository.findByCaseId("case-1")).thenReturn(Optional.of(automationCase));
        when(caseRepository.save(any(AutomationCaseEntity.class))).thenReturn(automationCase);
        when(interactionPlanner.generate(any())).thenReturn(Mono.just(
                new InteractionPlanner.PlanResult(
                        List.of(new InteractionPlanner.PlannedInteractionWithUser(
                                0, AutomationInteractionType.COMMENT, "u1", "pro-A", 50,
                                "content", null, null, null)),
                        null)));
        when(interactionExecutor.scheduleInteractions(any(), anyList(), any(Instant.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(new AutomationInteractionEntity()));

        orchestrator.launchRun(runId, false, 1, 3, 50, 3, 30);

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(run.getStartedAt()).isNotNull();
        verify(caseGenerator).generateCase(eq(runId), anyInt(), anyList(), anyList(), anyBoolean());
        verify(caseRepository).findByCaseId("case-1");
        verify(interactionPlanner).generate(any());
        verify(interactionExecutor).scheduleInteractions(
                any(), anyList(), any(Instant.class), anyInt(), anyInt(), anyInt());
        verify(wsController).broadcastQueueUpdate(any(Map.class));
    }

    @Test
    void launchRun_poolEmpty_autoEnablesBotsOnce() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        setId(run, runId);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        lenient().when(runRepository.save(any(AutomationRunEntity.class))).thenReturn(run);
        when(userSelector.selectDailyPool(anyInt()))
                .thenReturn(List.of())
                .thenReturn(List.of(new UserSelector.BotUser("u1", "bot1")));
        lenient().when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(Instant.class)))
                .thenReturn(List.of());
        when(identityJdbcTemplate.update(anyString())).thenReturn(1);
        when(caseGenerator.generateCase(eq(runId), anyInt(), anyList(), anyList(), anyBoolean()))
                .thenReturn(Mono.just(new CaseGenerator.CaseResult(null, AutomationCaseStatus.FAILED,
                        "u1", null, null)));

        orchestrator.launchRun(runId, false, 1, 3, 50, 3, 30);

        verify(identityJdbcTemplate).update(anyString());
        verify(userSelector, times(2)).selectDailyPool(anyInt());
    }

    @Test
    void launchRun_exception_marksRunFailed() {
        UUID runId = UUID.randomUUID();
        AutomationRunEntity run = new AutomationRunEntity();
        setId(run, runId);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        lenient().when(runRepository.save(any(AutomationRunEntity.class))).thenReturn(run);
        when(userSelector.selectDailyPool(anyInt())).thenThrow(new RuntimeException("pool broken"));

        orchestrator.launchRun(runId, false, 1, 3, 50, 3, 30);

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("pool broken");
        assertThat(run.getFinishedAt()).isNotNull();
    }
}
