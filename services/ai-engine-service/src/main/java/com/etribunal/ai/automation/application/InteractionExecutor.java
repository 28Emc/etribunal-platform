package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.domain.*;
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

    public InteractionExecutor(
            AutomationInteractionRepository interactionRepository,
            AutomationCaseRepository caseRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.interactionRepository = interactionRepository;
        this.caseRepository = caseRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        int windowMinutes = windowHours * 60;
        int effectiveInterval = Math.max(intervalMin,
                Math.min(intervalMax, windowMinutes / Math.max(1, totalInteractions)));

        List<AutomationInteractionEntity> scheduled = new ArrayList<>();
        Random jitter = new Random();

        for (int i = 0; i < totalInteractions; i++) {
            InteractionPlanner.PlannedInteractionWithUser planned = plannedInteractions.get(i);
            long offsetMinutes = (long) i * effectiveInterval + jitter.nextInt(Math.max(1, effectiveInterval / 3));
            Instant scheduledAt = baseTime.plusSeconds(offsetMinutes * 60);

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