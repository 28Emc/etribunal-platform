package com.etribunal.ai.automation.infrastructure.analytics;

import com.etribunal.ai.automation.domain.AutomationInteractionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Registra la actividad que generan los bots en la tabla de analytics compartida
 * (interaction_logs, gestionada por core-domain-service). Best-effort: un fallo de
 * escritura no debe romper la ejecución de la automatización.
 *
 * Sirve de semilla para el feedback loop de engagement (Fase 1 del plan 2.0).
 */
@Component
public class AnalyticsRecorder {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRecorder.class);

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordInteraction(AutomationInteractionType type, String caseId, String userId, String resultId) {
        try {
            String action = switch (type) {
                case VOTE -> "VOTE";
                case COMMENT, REPLY -> "COMMENT";
                case REACTION -> "REACTION";
            };
            jdbcTemplate.update(
                """
                INSERT INTO interaction_logs (action, case_id, user_id, metadata, created_at)
                VALUES (?, ?, ?, jsonb_build_object('source', 'ai-engine', 'result_id', ?), now())
                """,
                action,
                parseUuid(caseId),
                parseUuid(userId),
                resultId
            );
        } catch (Exception e) {
            log.warn("Could not record analytics for {} (case={}, user={}): {}",
                    type, caseId, userId, e.getMessage());
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
