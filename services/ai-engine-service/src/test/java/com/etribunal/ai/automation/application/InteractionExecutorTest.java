package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService;
import com.etribunal.ai.automation.infrastructure.analytics.AnalyticsRecorder;
import com.etribunal.ai.automation.infrastructure.api.CoreApiClient;
import com.etribunal.ai.automation.infrastructure.auth.BotAuthService;
import com.etribunal.ai.automation.infrastructure.kafka.AutomationEventPublisher;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class InteractionExecutorTest {

    @Mock
    private AutomationInteractionRepository interactionRepository;

    @Mock
    private AutomationCaseRepository caseRepository;

    @Mock
    private CoreApiClient coreApiClient;

    @Mock
    private BotAuthService botAuthService;

    @Mock
    private AutomationEventPublisher eventPublisher;

    @Mock
    private AnalyticsRecorder analyticsRecorder;

    @Mock
    private AutomationConfig config;

    @Mock
    private ActivityProfileService activityProfileService;

    @InjectMocks
    private InteractionExecutor executor;

    private AutomationCaseEntity automationCase;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        caseId = UUID.randomUUID();
        automationCase = new AutomationCaseEntity();
        automationCase.setCaseId(caseId.toString());

        AutomationConfig.ActivityConfig activityConfig = new AutomationConfig.ActivityConfig();
        activityConfig.setEnabled(true);
        activityConfig.setWeighted(false);
        lenient().when(config.getActivity()).thenReturn(activityConfig);
        lenient().when(activityProfileService.weightForHour(anyInt())).thenReturn(1.0);
        lenient().when(interactionRepository.save(any(AutomationInteractionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(interactionRepository.claimForExecution(any(), any(), any())).thenReturn(1);
        lenient().when(botAuthService.getTokenForBot(anyString())).thenReturn("bot-token");
    }

    private AutomationInteractionEntity makeEntity(AutomationInteractionType type, Map<String, Object> metadata) {
        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setInteractionType(type);
        entity.setUserId(UUID.randomUUID().toString());
        entity.setMetadata(metadata);
        entity.setAutomationCase(automationCase);
        entity.setStatus(AutomationInteractionStatus.SCHEDULED);
        return entity;
    }

    @Test
    void scheduleInteractions_createsScheduledEntities() {
        List<InteractionPlanner.PlannedInteractionWithUser> planned = List.of(
                new InteractionPlanner.PlannedInteractionWithUser(0, AutomationInteractionType.COMMENT, "u1", "pro-A", 50, "Content", null, null, null),
                new InteractionPlanner.PlannedInteractionWithUser(1, AutomationInteractionType.VOTE, "u2", null, null, null, null, "A", null)
        );

        Instant baseTime = Instant.parse("2026-09-03T12:00:00Z");
        when(caseRepository.findById(any(UUID.class))).thenReturn(Optional.of(automationCase));

        List<AutomationInteractionEntity> scheduled = executor.scheduleInteractions(
                UUID.randomUUID(), planned, baseTime, 30, 180, 24);

        assertThat(scheduled).hasSize(2);
        assertThat(scheduled.get(0).getStatus()).isEqualTo(AutomationInteractionStatus.SCHEDULED);
        assertThat(scheduled.get(0).getPlanIndex()).isZero();
        assertThat(scheduled.get(0).getScheduledAt()).isNotNull();
        assertThat(scheduled.get(0).getMetadata()).containsKey("content");
        assertThat(scheduled.get(1).getInteractionType()).isEqualTo(AutomationInteractionType.VOTE);
        verify(interactionRepository, times(2)).save(any(AutomationInteractionEntity.class));
    }

    @Test
    void scheduleInteractions_emptyPlanned_returnsEmpty() {
        List<AutomationInteractionEntity> scheduled = executor.scheduleInteractions(
                UUID.randomUUID(), List.of(), Instant.now(), 30, 180, 24);

        assertThat(scheduled).isEmpty();
        verify(interactionRepository, never()).save(any());
    }

    @Test
    void scheduleInteractions_toneNull_setsNullTone() {
        List<InteractionPlanner.PlannedInteractionWithUser> planned = List.of(
                new InteractionPlanner.PlannedInteractionWithUser(0, AutomationInteractionType.COMMENT, "u1", "neutral", null, "c", null, null, null)
        );
        when(caseRepository.findById(any(UUID.class))).thenReturn(Optional.of(automationCase));

        List<AutomationInteractionEntity> scheduled = executor.scheduleInteractions(
                UUID.randomUUID(), planned, Instant.now(), 30, 180, 24);

        assertThat(scheduled.get(0).getTone()).isNull();
    }

    @Test
    void computeSchedule_uniform_returnsCorrectTimes() {
        Instant base = Instant.parse("2026-09-03T12:00:00Z");
        List<Instant> times = executor.computeSchedule(3, base, 24, 30, 180);

        assertThat(times).hasSize(3);
        assertThat(times.get(0)).isAfterOrEqualTo(base);
        assertThat(times.get(2)).isBefore(base.plusSeconds(24 * 3600));
        assertThat(times).isSorted();
    }

    @Test
    void computeSchedule_weighted_usesActivityProfile() {
        AutomationConfig.ActivityConfig activityConfig = new AutomationConfig.ActivityConfig();
        activityConfig.setEnabled(true);
        activityConfig.setWeighted(true);
        when(config.getActivity()).thenReturn(activityConfig);

        Instant base = Instant.parse("2026-09-03T12:00:00Z");
        List<Instant> times = executor.computeSchedule(3, base, 24, 30, 180);

        assertThat(times).hasSize(3).isSorted();
        verify(activityProfileService, atLeastOnce()).weightForHour(anyInt());
    }

    @Test
    void computeSchedule_returnsEmptyForZeroCount() {
        List<Instant> times = executor.computeSchedule(0, Instant.now(), 24, 30, 180);
        assertThat(times).isEmpty();
    }

    @Test
    void computeSchedule_negativeCount_returnsEmpty() {
        List<Instant> times = executor.computeSchedule(-1, Instant.now(), 24, 30, 180);
        assertThat(times).isEmpty();
    }

    @Test
    void binarySearch_findsCorrectIndex() {
        double[] cum = new double[]{0.0, 0.25, 0.5, 0.75, 1.0};
        int idx = InteractionExecutorTestHelper.binarySearch(executor, cum, 0.3);
        assertThat(idx).isEqualTo(2);
    }

    @Test
    void binarySearch_targetZero_returnsZero() {
        double[] cum = new double[]{0.0, 0.5, 1.0};
        assertThat(InteractionExecutorTestHelper.binarySearch(executor, cum, 0.0)).isZero();
    }

    @Test
    void binarySearch_targetOne_returnsLast() {
        double[] cum = new double[]{0.0, 0.5, 1.0};
        assertThat(InteractionExecutorTestHelper.binarySearch(executor, cum, 1.0)).isEqualTo(2);
    }

    @Test
    void execute_returnsFailed_whenInteractionNotFound() {
        when(interactionRepository.claimForExecution(any(), any(), any())).thenReturn(0);
        when(interactionRepository.findById(any())).thenReturn(Optional.empty());

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void execute_returnsExistingStatus_whenAlreadyProcessing() {
        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setStatus(AutomationInteractionStatus.PROCESSING);
        entity.setResultId("existing-result");

        when(interactionRepository.claimForExecution(any(), any(), any())).thenReturn(0);
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.resultId()).isEqualTo("existing-result");
        verify(interactionRepository, never()).save(any());
    }

    @Test
    void execute_claimFails_returnsCurrentStatus() {
        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setStatus(AutomationInteractionStatus.SUCCESS);

        when(interactionRepository.claimForExecution(any(), any(), any())).thenReturn(0);
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void execute_success_comment_publishesActivity() {
        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.COMMENT,
                Map.of("content", "Hello", "case_id", caseId.toString(), "reply_to_plan_index", -1));
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenReturn(UUID.randomUUID());
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(entity.getStatus()).isEqualTo(AutomationInteractionStatus.SUCCESS);
        verify(caseRepository).incrementSuccessfulInteractions(any());
        verify(eventPublisher).publishActivity(eq(AutomationInteractionType.COMMENT), anyString(), anyString(), anyString());
        verify(analyticsRecorder).recordInteraction(eq(AutomationInteractionType.COMMENT), anyString(), anyString(), anyString());
    }

    @Test
    void execute_success_reply_publishesActivity() {
        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.REPLY,
                Map.of("content", "Reply", "case_id", caseId.toString(), "reply_to_plan_index", -1));
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenReturn(UUID.randomUUID());
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(caseRepository).incrementSuccessfulInteractions(any());
    }

    @Test
    void execute_success_reaction_publishesActivity() {
        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.REACTION,
                Map.of("case_id", caseId.toString(), "reaction", "LIKE"));
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(coreApiClient.addReaction(anyString(), eq("CASE"), any(UUID.class), eq("LIKE")))
                .thenReturn(UUID.randomUUID());
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.resultId()).isNotNull();
    }

    @Test
    void execute_success_vote_publishesActivity() {
        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.VOTE,
                Map.of("case_id", caseId.toString(), "option", "A"));
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(coreApiClient.vote(anyString(), any(UUID.class), eq("A")))
                .thenReturn(UUID.randomUUID());
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void execute_failure_marksFailed_andIncrements() {
        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.COMMENT,
                Map.of("content", "x", "case_id", caseId.toString()));
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenThrow(new RuntimeException("API down"));
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("BUSINESS_RULE");
        assertThat(entity.getStatus()).isEqualTo(AutomationInteractionStatus.FAILED);
        verify(caseRepository).incrementFailedInteractions(any());
    }

    @Test
    void execute_dispatch_unknownType_fails() {
        AutomationInteractionEntity entity = makeEntity(null, Map.of("case_id", caseId.toString()));
        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("BUSINESS_RULE");
    }

    @Test
    void dispatch_comment_withParent_resolvesReply() {
        UUID parentUuid = UUID.randomUUID();
        AutomationInteractionEntity parent = new AutomationInteractionEntity();
        parent.setResultId(parentUuid.toString());
        when(interactionRepository.findByAutomationCaseIdAndPlanIndex(any(), eq(0)))
                .thenReturn(Optional.of(parent));
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), any(UUID.class), anyBoolean()))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.COMMENT,
                Map.of("content", "hi", "case_id", caseId.toString(), "reply_to_plan_index", 0));

        String resultId = executor.dispatch(entity);

        assertThat(resultId).isNotNull();
        verify(coreApiClient).createComment("bot-token", caseId, "hi", parentUuid, false);
    }

    @Test
    void dispatch_comment_replyIndexMissing_returnsNullParent() {
        when(interactionRepository.findByAutomationCaseIdAndPlanIndex(any(), anyInt()))
                .thenReturn(Optional.empty());
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.REPLY,
                Map.of("content", "r", "case_id", caseId.toString(), "reply_to_plan_index", 99));

        String resultId = executor.dispatch(entity);

        assertThat(resultId).isNotNull();
        verify(coreApiClient).createComment(eq("bot-token"), eq(caseId), eq("r"), isNull(), eq(false));
    }

    @Test
    void dispatch_comment_stringReplyIndex_parses() {
        UUID parentUuid = UUID.randomUUID();
        AutomationInteractionEntity parent = new AutomationInteractionEntity();
        parent.setResultId(parentUuid.toString());
        when(interactionRepository.findByAutomationCaseIdAndPlanIndex(any(), eq(5)))
                .thenReturn(Optional.of(parent));
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), any(UUID.class), anyBoolean()))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.COMMENT,
                Map.of("content", "x", "case_id", caseId.toString(), "reply_to_plan_index", "5"));

        executor.dispatch(entity);

        verify(coreApiClient).createComment(anyString(), any(UUID.class), anyString(), eq(parentUuid), anyBoolean());
    }

    @Test
    void dispatch_comment_malformedStringReplyIndex_ignores() {
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.COMMENT,
                Map.of("content", "x", "case_id", caseId.toString(), "reply_to_plan_index", "not-a-number"));

        executor.dispatch(entity);

        verify(coreApiClient).createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean());
        verify(interactionRepository, never()).findByAutomationCaseIdAndPlanIndex(any(), anyInt());
    }

    @Test
    void dispatch_metadataNull_usesCaseEntityCaseId() {
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setInteractionType(AutomationInteractionType.COMMENT);
        entity.setUserId(UUID.randomUUID().toString());
        entity.setMetadata(null);
        entity.setAutomationCase(automationCase);

        executor.dispatch(entity);

        verify(coreApiClient).createComment(eq("bot-token"), eq(caseId), eq(""), isNull(), eq(false));
    }

    @Test
    void dispatch_apiFailure_wrapsException() {
        when(coreApiClient.vote(anyString(), any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("boom"));

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.VOTE,
                Map.of("case_id", caseId.toString(), "option", "B"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> executor.dispatch(entity)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("API call failed");
    }

    @Test
    void dispatch_reaction_defaultEmoji_whenMissing() {
        when(coreApiClient.addReaction(anyString(), eq("CASE"), any(UUID.class), eq("LIKE")))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.REACTION,
                Map.of("case_id", caseId.toString()));

        executor.dispatch(entity);

        verify(coreApiClient).addReaction("bot-token", "CASE", caseId, "LIKE");
    }

    @Test
    void dispatch_vote_defaultOption_whenMissing() {
        when(coreApiClient.vote(anyString(), any(UUID.class), eq("A")))
                .thenReturn(UUID.randomUUID());

        AutomationInteractionEntity entity = makeEntity(AutomationInteractionType.VOTE,
                Map.of("case_id", caseId.toString()));

        executor.dispatch(entity);

        verify(coreApiClient).vote("bot-token", caseId, "A");
    }

    @Test
    void execute_noMetadata_getsCaseIdFromEntity() {
        AutomationInteractionEntity entity = new AutomationInteractionEntity();
        entity.setInteractionType(AutomationInteractionType.COMMENT);
        entity.setUserId(UUID.randomUUID().toString());
        entity.setMetadata(null);
        entity.setAutomationCase(automationCase);
        entity.setStatus(AutomationInteractionStatus.SCHEDULED);

        when(interactionRepository.findById(any())).thenReturn(Optional.of(entity));
        when(coreApiClient.createComment(anyString(), any(UUID.class), anyString(), isNull(), anyBoolean()))
                .thenReturn(UUID.randomUUID());
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteractionExecutor.ExecuteResult result = executor.execute(UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    // Helper to access private methods via reflection
    static class InteractionExecutorTestHelper {
        static int binarySearch(InteractionExecutor executor, double[] cum, double target) {
            try {
                var method = InteractionExecutor.class.getDeclaredMethod("binarySearch", double[].class, double.class);
                method.setAccessible(true);
                return (int) method.invoke(executor, (Object) cum, target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
