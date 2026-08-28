package com.etribunal.core.analytics;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra interacciones de usuario (best-effort, nunca bloquea ni hace rollback
 * de la operación principal — parity legacy AnalyticsService).
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final InteractionLogRepository repository;

    public AnalyticsService(InteractionLogRepository repository) {
        this.repository = repository;
    }

    public void log(String action, UUID caseId, UUID userId) {
        log(action, caseId, userId, Map.of());
    }

    public void log(String action, UUID caseId, UUID userId,
                    Map<String, Object> metadata) {
        try {
            InteractionLogEntity entity = new InteractionLogEntity();
            entity.setAction(action);
            entity.setCaseId(caseId);
            entity.setUserId(userId);
            entity.setMetadata(metadata);
            repository.save(entity);
        } catch (Exception e) {
            log.error("Error logging interaction {} for case {}: {}",
                    action, caseId, e.getMessage());
        }
    }

    /**
     * KPI globales: total por acción desde la última semana + usuarios activos.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalKPIs() {
        java.time.Instant since =
                java.time.Instant.now().minus(java.time.Duration.ofDays(7));
        Map<String, Long> byAction = repository.countByActionSince(since).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));
        long activeUsers = repository.countByUserIdIsNotNullAndCreatedAtAfter(since);
        return Map.of(
                "byAction", byAction,
                "active_users", activeUsers,
                "since", since.toString());
    }

    /**
     * KPI por caso: total por acción (keys snake_case lowercase, parity legacy).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCaseKPIs(UUID caseId) {
        Map<String, Long> byAction = repository.countByActionForCase(caseId).stream()
                .collect(Collectors.toMap(
                        row -> ((String) row[0]).toLowerCase(),
                        row -> ((Number) row[1]).longValue()));
        return Map.of("case_id", caseId.toString(), "byAction", byAction);
    }
}