package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.*;
import com.etribunal.ai.automation.infrastructure.api.CoreApiClient;
import com.etribunal.ai.automation.infrastructure.auth.BotAuthService;
import com.etribunal.ai.automation.infrastructure.analytics.ActivityProfileService;
import com.etribunal.ai.automation.infrastructure.analytics.AnalyticsRecorder;
import com.etribunal.ai.automation.infrastructure.kafka.AutomationEventPublisher;
import com.etribunal.ai.automation.repository.AutomationCaseRepository;
import com.etribunal.ai.automation.repository.AutomationInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InteractionExecutor {

    private static final Logger log = LoggerFactory.getLogger(InteractionExecutor.class);
    private static final int TICK_BATCH = 5;

    private final AutomationInteractionRepository interactionRepository;
    private final AutomationCaseRepository caseRepository;
    private final CoreApiClient coreApiClient;
    private final BotAuthService botAuthService;
    private final AutomationEventPublisher eventPublisher;
    private final AnalyticsRecorder analyticsRecorder;
    private final AutomationConfig config;
    private final ActivityProfileService activityProfileService;

    /**
     * Self-proxy lazy: permite invocar {@link #dispatch(AutomationInteractionEntity)} a través del
     * proxy de Spring para que retorne en una transacción independiente (REQUIRES_NEW). Así un fallo
     * de API en UNA interacción revierte solo su subtransacción y no aborta el tick completo,
     * permitiendo persistir el estado FAILED y que el resto de interacciones siga ejecutándose.
     */
    @Autowired
    @Lazy
    private InteractionExecutor self;

    void setSelf(InteractionExecutor self) {
        this.self = self;
    }

    public InteractionExecutor(
            AutomationInteractionRepository interactionRepository,
            AutomationCaseRepository caseRepository,
            CoreApiClient coreApiClient,
            BotAuthService botAuthService,
            AutomationEventPublisher eventPublisher,
            AnalyticsRecorder analyticsRecorder,
            AutomationConfig config,
            ActivityProfileService activityProfileService
    ) {
        this.interactionRepository = interactionRepository;
        this.caseRepository = caseRepository;
        this.coreApiClient = coreApiClient;
        this.botAuthService = botAuthService;
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
        ThreadLocalRandom jitter = ThreadLocalRandom.current();
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

        ThreadLocalRandom jitter = ThreadLocalRandom.current();
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
            InteractionExecutor dispatchSource = self != null ? self : this;
            String resultId = dispatchSource.dispatch(entity);
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

    /**
     * Ejecuta la acción concreta (comentario/reply/reacción/voto) vía API autenticada del core.
     * Se invoca vía el self-proxy para que cada interacción tenga su propia transacción
     * (REQUIRES_NEW). Un fallo de API en UNA interacción revierte solo su subtransacción.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String dispatch(AutomationInteractionEntity entity) {
        Map<String, Object> metadata = entity.getMetadata() == null ? Map.of() : entity.getMetadata();
        String caseId = getCaseIdFromEntity(entity);
        String userId = entity.getUserId();

        // Obtener el token del bot propietario de la interacción (login real vía API)
        String token = botAuthService.getTokenForBot(userId);

        try {
            return switch (entity.getInteractionType()) {
                case COMMENT -> {
                    String content = metadata.getOrDefault("content", "").toString();
                    String parentCommentId = resolveReplyParent(entity.getAutomationCase().getId(), metadata);
                    yield coreApiClient.createComment(token, UUID.fromString(caseId), content,
                            parentCommentId != null ? UUID.fromString(parentCommentId) : null, false).toString();
                }
                case REPLY -> {
                    String content = metadata.getOrDefault("content", "").toString();
                    String parentCommentId = resolveReplyParent(entity.getAutomationCase().getId(), metadata);
                    yield coreApiClient.createComment(token, UUID.fromString(caseId), content,
                            parentCommentId != null ? UUID.fromString(parentCommentId) : null, false).toString();
                }
                case REACTION -> {
                    String emoji = metadata.getOrDefault("reaction", "LIKE").toString();
                    yield coreApiClient.addReaction(token, "CASE", UUID.fromString(caseId), emoji).toString();
                }
                case VOTE -> {
                    String option = metadata.getOrDefault("option", "A").toString();
                    yield coreApiClient.vote(token, UUID.fromString(caseId), option).toString();
                }
                default -> throw new IllegalArgumentException("Unknown interaction type: " + entity.getInteractionType());
            };
        } catch (Exception e) {
            log.error("API call failed for interaction {}: {}", entity.getId(), e.getMessage());
            throw e;
        }
    }

    private String resolveReplyParent(UUID automationCaseId, Map<String, Object> metadata) {
        int replyToIndex = -1;
        Object obj = metadata.get("reply_to_plan_index");
        if (obj instanceof Integer) {
            replyToIndex = (Integer) obj;
        } else if (obj instanceof String) {
            try {
                replyToIndex = Integer.parseInt((String) obj);
            } catch (NumberFormatException ignored) {}
        }
        if (replyToIndex < 0) {
            return null;
        }
        return interactionRepository
                .findByAutomationCaseIdAndPlanIndex(automationCaseId, replyToIndex)
                .map(AutomationInteractionEntity::getResultId)
                .orElse(null);
    }

    private String getCaseIdFromEntity(AutomationInteractionEntity entity) {
        Map<String, Object> metadata = entity.getMetadata() == null ? Map.of() : entity.getMetadata();
        String caseId = (String) metadata.get("case_id");
        if (caseId == null) {
            caseId = entity.getAutomationCase().getCaseId();
        }
        return caseId;
    }
}