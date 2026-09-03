package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService;
import com.etribunal.ai.automation.infrastructure.analytics.AnalyticsRecorder;
import com.etribunal.ai.automation.infrastructure.kafka.AutomationEventPublisher;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class InteractionExecutor {

    private static final Logger log = LoggerFactory.getLogger(InteractionExecutor.class);
    private static final int TICK_BATCH = 5;

    private final AutomationInteractionRepository interactionRepository;
    private final AutomationCaseRepository caseRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AutomationEventPublisher eventPublisher;
    private final AnalyticsRecorder analyticsRecorder;
    private final AutomationConfig config;
    private final ActivityProfileService activityProfileService;

    public InteractionExecutor(
            AutomationInteractionRepository interactionRepository,
            AutomationCaseRepository caseRepository,
            JdbcTemplate jdbcTemplate,
            AutomationEventPublisher eventPublisher,
            AnalyticsRecorder analyticsRecorder,
            AutomationConfig config,
            ActivityProfileService activityProfileService
    ) {
        this.interactionRepository = interactionRepository;
        this.caseRepository = caseRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.analyticsRecorder = analyticsRecorder;
        this.config = config;
        this.activityProfileService = activityProfileService;
    }

    public record ExecuteResult(
        String status,
        String resultId,
        String errorCode,
        String errorMessage
    ) {}

    @Transactional
    public List<AutomationInteractionEntity> scheduleInteractions(
            UUID automationCaseId,
            List<InteractionPlanner.PlannedInteractionWithUser> plannedInteractions,
            Instant baseTime,
            int intervalMin,
            int intervalMax,
            int windowHours
    ) {
        int totalInteractions = plannedInteractions.size();
        List<Instant> scheduleTimes = computeSchedule(totalInteractions, baseTime, windowHours, intervalMin, intervalMax);

        List<AutomationInteractionEntity> scheduled = new ArrayList<>();

        for (int i = 0; i < totalInteractions; i++) {
            InteractionPlanner.PlannedInteractionWithUser planned = plannedInteractions.get(i);
            Instant scheduledAt = scheduleTimes.get(i);

            AutomationInteractionEntity entity = new AutomationInteractionEntity();
            entity.setAutomationCase(caseRepository.findById(automationCaseId).orElseThrow());
            entity.setUserId(planned.userId());
            entity.setInteractionType(planned.type());
            entity.setStatus(AutomationInteractionStatus.SCHEDULED);
            entity.setStance(planned.stance());
            entity.setTone(planned.tone() != null ? String.valueOf(planned.tone()) : null);
            entity.setPlanIndex(planned.index());
            entity.setScheduledAt(scheduledAt);
            entity.setMetadata(Map.of(
                    "content", planned.content() != null ? planned.content() : "",
                    "reaction", planned.reaction() != null ? planned.reaction() : "",
                    "option", planned.option() != null ? planned.option() : "",
                    "reply_to_plan_index", planned.replyToCommentIndex() != null ? planned.replyToCommentIndex() : -1
            ));

            scheduled.add(interactionRepository.save(entity));
        }

        return scheduled;
    }

    /**
     * Distribuye las interacciones en la ventana. Si el scheduling ponderado está
     * habilitado, concentra los scheduledAt en las horas pico del perfil de actividad
     * (con jitter y separación mínima). Si no, usa intervalos uniformes con jitter aleatorio.
     */
    List<Instant> computeSchedule(int count, Instant baseTime, int windowHours,
            int intervalMin, int intervalMax) {
        if (count <= 0) {
            return List.of();
        }
        AutomationConfig.ActivityConfig a = config.getActivity();
        if (a.isEnabled() && a.isWeighted()) {
            return weightedSchedule(count, baseTime, windowHours, intervalMin);
        }
        return uniformSchedule(count, baseTime, windowHours, intervalMin, intervalMax);
    }

    private List<Instant> uniformSchedule(int count, Instant baseTime, int windowHours,
            int intervalMin, int intervalMax) {
        int windowMinutes = windowHours * 60;
        int effectiveInterval = Math.max(intervalMin,
                Math.min(intervalMax, windowMinutes / Math.max(1, count)));
        Random jitter = new Random();
        List<Instant> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offsetMinutes = (long) i * effectiveInterval + jitter.nextInt(Math.max(1, effectiveInterval / 3));
            out.add(baseTime.plusSeconds(offsetMinutes * 60));
        }
        return out;
    }

    private List<Instant> weightedSchedule(int count, Instant baseTime, int windowHours, int intervalMin) {
        int windowMinutes = windowHours * 60;
        double[] cum = new double[windowMinutes + 1];
        for (int m = 0; m < windowMinutes; m++) {
            Instant minuteInstant = baseTime.plusSeconds(m * 60L);
            int hour = minuteInstant.atZone(java.time.ZoneOffset.UTC).getHour();
            cum[m + 1] = cum[m] + activityProfileService.weightForHour(hour);
        }
        double total = cum[windowMinutes];
        if (total <= 0) {
            for (int m = 1; m <= windowMinutes; m++) {
                cum[m] = m / (double) windowMinutes;
            }
            total = 1.0;
        } else {
            for (int m = 1; m <= windowMinutes; m++) {
                cum[m] /= total;
            }
        }

        Random jitter = new Random();
        List<Instant> out = new ArrayList<>(count);
        long prevMinute = Long.MIN_VALUE;
        int minGap = Math.max(1, intervalMin);
        for (int i = 0; i < count; i++) {
            double target = (i + 0.5) / count;
            int minute = binarySearch(cum, target);
            minute = Math.min(minute, windowMinutes - 1);
            if (prevMinute != Long.MIN_VALUE && minute < prevMinute + minGap) {
                minute = (int) (prevMinute + minGap);
            }
            minute = Math.min(minute, windowMinutes - 1);
            int jitterMinutes = jitter.nextInt(Math.min(minGap / 2 + 1, 15) + 1);
            minute = Math.min(minute + jitterMinutes, windowMinutes - 1);
            prevMinute = minute;
            out.add(baseTime.plusSeconds(minute * 60L));
        }
        return out;
    }

    private int binarySearch(double[] cum, double target) {
        int lo = 0, hi = cum.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    @Transactional
    public ExecuteResult execute(UUID interactionId) {
        Optional<AutomationInteractionEntity> opt = interactionRepository.findById(interactionId);
        if (opt.isEmpty()) {
            return new ExecuteResult("FAILED", null, "NOT_FOUND", "Interaction not found");
        }

        AutomationInteractionEntity entity = opt.get();

        if (entity.getStatus() == AutomationInteractionStatus.SUCCESS ||
            entity.getStatus() == AutomationInteractionStatus.FAILED ||
            entity.getStatus() == AutomationInteractionStatus.REJECTED) {
            return new ExecuteResult(entity.getStatus().name(), entity.getResultId(), entity.getErrorCode(), entity.getErrorMessage());
        }

        entity.setStatus(AutomationInteractionStatus.PROCESSING);
        interactionRepository.save(entity);

        try {
            String resultId = dispatch(entity);
            entity.setStatus(AutomationInteractionStatus.SUCCESS);
            entity.setResultId(resultId);
            entity.setExecutedAt(Instant.now());
            interactionRepository.save(entity);

            if (resultId != null) {
                caseRepository.incrementSuccessfulInteractions(entity.getAutomationCase().getId());
                String caseId = getCaseIdFromEntity(entity);
                eventPublisher.publishActivity(entity.getInteractionType(), caseId, entity.getUserId(), resultId);
                analyticsRecorder.record(entity.getInteractionType(), caseId, entity.getUserId(), resultId);
            }

            return new ExecuteResult("SUCCESS", resultId, null, null);

        } catch (Exception e) {
            log.error("Interaction {} failed: {}", interactionId, e.getMessage());
            entity.setStatus(AutomationInteractionStatus.FAILED);
            entity.setErrorMessage(e.getMessage());
            entity.setErrorCode("BUSINESS_RULE");
            entity.setExecutedAt(Instant.now());
            interactionRepository.save(entity);

            caseRepository.incrementFailedInteractions(entity.getAutomationCase().getId());

            return new ExecuteResult("FAILED", null, "BUSINESS_RULE", e.getMessage());
        }
    }

    private String dispatch(AutomationInteractionEntity entity) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = entity.getMetadata() instanceof Map ? (Map<String, Object>) entity.getMetadata() : Map.of();
        String caseId = getCaseIdFromEntity(entity);
        String userId = entity.getUserId();

        switch (entity.getInteractionType()) {
            case COMMENT -> {
                String content = metadata.getOrDefault("content", "").toString();
                return createComment(caseId, userId, content, null);
            }
            case REPLY -> {
                String content = metadata.getOrDefault("content", "").toString();
                Integer replyToIndex = (Integer) metadata.getOrDefault("reply_to_plan_index", -1);
                String parentCommentId = resolveParentCommentId(entity.getAutomationCase().getId(), replyToIndex);
                return createComment(caseId, userId, content, parentCommentId);
            }
            case REACTION -> {
                String emoji = metadata.getOrDefault("reaction", "LIKE").toString();
                return addReaction(caseId, userId, emoji);
            }
            case VOTE -> {
                String option = metadata.getOrDefault("option", "A").toString();
                return createVote(caseId, userId, option);
            }
            default -> throw new IllegalArgumentException("Unknown interaction type: " + entity.getInteractionType());
        }
    }

    private String createComment(String caseId, String userId, String content, String parentCommentId) {
        String commentId = UUID.randomUUID().toString();
        if (parentCommentId != null) {
            jdbcTemplate.update(
                "INSERT INTO comments (id, case_id, user_id, parent_id, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                commentId, caseId, userId, parentCommentId, content, Instant.now(), Instant.now()
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO comments (id, case_id, user_id, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                commentId, caseId, userId, content, Instant.now(), Instant.now()
            );
        }
        return commentId;
    }

    private String addReaction(String caseId, String userId, String emoji) {
        String reactionId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO reactions (id, case_id, user_id, emoji, created_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (case_id, user_id, emoji) DO NOTHING",
            reactionId, caseId, userId, emoji, Instant.now()
        );
        return reactionId;
    }

    private String createVote(String caseId, String userId, String option) {
        String voteId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO votes (id, case_id, user_id, vote_type, created_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (case_id, user_id) DO UPDATE SET vote_type = ?, updated_at = ?",
            voteId, caseId, userId, option, Instant.now(), option, Instant.now()
        );
        return voteId;
    }

    private String resolveParentCommentId(UUID automationCaseId, int replyToPlanIndex) {
        if (replyToPlanIndex < 0) return null;

        Optional<AutomationInteractionEntity> parentInteraction = interactionRepository
                .findByAutomationCaseIdAndPlanIndex(automationCaseId, replyToPlanIndex);

        return parentInteraction
                .map(AutomationInteractionEntity::getResultId)
                .orElse(null);
    }

    private String getCaseIdFromEntity(AutomationInteractionEntity entity) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = entity.getMetadata() instanceof Map ? (Map<String, Object>) entity.getMetadata() : Map.of();
        String caseId = (String) metadata.get("case_id");
        if (caseId == null) {
            caseId = entity.getAutomationCase().getCaseId();
        }
        return caseId;
    }
}