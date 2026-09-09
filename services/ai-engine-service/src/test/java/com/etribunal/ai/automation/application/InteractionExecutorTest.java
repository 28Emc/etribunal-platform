package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService;
import com.etribunal.ai.automation.infrastructure.analytics.AnalyticsRecorder;
import com.etribunal.ai.automation.infrastructure.kafka.AutomationEventPublisher;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class InteractionExecutorTest {

    @Mock
    private AutomationInteractionRepository interactionRepository;
    @Mock
    private AutomationCaseRepository caseRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AutomationEventPublisher eventPublisher;
    @Mock
    private AnalyticsRecorder analyticsRecorder;
    @Mock
    private ActivityProfileService activityProfileService;
    @Spy
    private AutomationConfig config = new AutomationConfig();

    @InjectMocks
    private InteractionExecutor executor;

    @Test
    void execute_returnsFailed_whenInteractionNotFound() {
        when(interactionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void execute_returnsSuccess_whenAlreadyCompleted() {
        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setStatus(AutomationInteractionStatus.SUCCESS);
        entity.setResultId("existing-result");

        when(interactionRepository.findById(any(UUID.class))).thenReturn(Optional.of(entity));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.resultId()).isEqualTo("existing-result");
    }

    @Test
    void scheduleInteractions_createsScheduledEntities() {
        UUID caseId = UUID.randomUUID();
        AutomationCaseEntity caseEntity = new AutomationCaseEntity();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));

        List<InteractionPlanner.PlannedInteractionWithUser> planned = List.of(
                new InteractionPlanner.PlannedInteractionWithUser(0, AutomationInteractionType.COMMENT, "u1", "pro-A", 50, "Content", null, null, null),
                new InteractionPlanner.PlannedInteractionWithUser(1, AutomationInteractionType.VOTE, "u2", null, null, null, null, "A", null)
        );

        when(interactionRepository.save(any(AutomationInteractionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<AutomationInteractionEntity> result = executor.scheduleInteractions(
                caseId, planned, Instant.now(), 30, 180, 24
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getInteractionType()).isEqualTo(AutomationInteractionType.COMMENT);
        assertThat(result.get(0).getStatus()).isEqualTo(AutomationInteractionStatus.SCHEDULED);
        assertThat(result.get(1).getInteractionType()).isEqualTo(AutomationInteractionType.VOTE);
    }

    @Test
    void execute_publishesEventAndAnalytics_onSuccessfulInteraction() {
        String caseUuid = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();
        AutomationCaseEntity caseEntity = new AutomationCaseEntity();
        caseEntity.setCaseId(caseUuid);

        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setAutomationCase(caseEntity);
        entity.setUserId(userUuid);
        entity.setInteractionType(AutomationInteractionType.COMMENT);
        entity.setStatus(AutomationInteractionStatus.SCHEDULED);
        entity.setMetadata(new HashMap<>(Map.of("content", "Hola", "case_id", caseUuid)));

        when(interactionRepository.findById(any(UUID.class))).thenReturn(Optional.of(entity));
        when(interactionRepository.save(any(AutomationInteractionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(eventPublisher).publishActivity(
                eq(AutomationInteractionType.COMMENT), eq(caseUuid), eq(userUuid), anyString());
        verify(analyticsRecorder).record(
                eq(AutomationInteractionType.COMMENT), eq(caseUuid), eq(userUuid), anyString());
        verify(caseRepository).incrementSuccessfulInteractions(isNull());
    }

    @Test
    void computeSchedule_spreadsUniformly_whenWeightedDisabled() {
        AutomationConfig cfg = new AutomationConfig();
        cfg.getActivity().setWeighted(false);
        cfg.getActivity().setEnabled(true);
        InteractionExecutor ex = new InteractionExecutor(
                interactionRepository, caseRepository, jdbcTemplate,
                eventPublisher, analyticsRecorder, cfg, activityProfileService);

        Instant base = Instant.parse("2026-09-03T09:00:00Z");
        List<Instant> times = ex.computeSchedule(3, base, 24, 30, 180);

        assertThat(times).hasSize(3);
        assertThat(times.get(0)).isAfterOrEqualTo(base);
        assertThat(times.get(2)).isBefore(base.plusSeconds(24 * 3600));
        assertThat(times).isSorted();
    }

    @Test
    void computeSchedule_concentratesInPeakHour_whenWeightedEnabled() {
        AutomationConfig cfg = new AutomationConfig();
        cfg.getActivity().setWeighted(true);
        cfg.getActivity().setEnabled(true);
        InteractionExecutor ex = new InteractionExecutor(
                interactionRepository, caseRepository, jdbcTemplate,
                eventPublisher, analyticsRecorder, cfg, activityProfileService);

        // Perfil con pico fuerte a las 20:00 (hora UTC)
        when(activityProfileService.weightForHour(anyInt())).thenReturn(0.02);
        when(activityProfileService.weightForHour(eq(20))).thenReturn(0.5);

        Instant base = Instant.parse("2026-09-03T09:00:00Z");
        List<Instant> times = ex.computeSchedule(4, base, 24, 30, 180);

        assertThat(times).hasSize(4);
        // La mayoría de las interacciones debe caer cerca del pico (20:00 vs base 09:00 → offset ~11h)
        long peakProximity = times.stream()
                .filter(t -> t.atZone(java.time.ZoneOffset.UTC).getHour() == 20)
                .count();
        assertThat(peakProximity).isGreaterThan(0);
    }
}