package com.etribunal.ai.automation.infrastructure.kafka;

import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.common.domain.event.CaseCreatedEvent;
import com.etribunal.common.kafka.EventJson;
import com.etribunal.common.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publica eventos de actividad hacia el resto del sistema (core-domain) cuando el engine
 * crea casos o ejecuta interacciones vía JDBC directo. Best-effort: si Kafka está caído,
 * el flujo de automatización sigue sin bloquear ni propagar errores.
 */
@Component
public class AutomationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AutomationEventPublisher.class);
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public AutomationEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCaseCreated(UUID caseId, UUID sideAUserId, UUID sideBUserId, String caseType) {
        try {
            CaseCreatedEvent event = new CaseCreatedEvent(caseId, sideAUserId, sideBUserId, caseType);
            byte[] bytes = EventJson.serialize(event);
            kafkaTemplate.send(Topics.CASE_EVENTS, caseId.toString(), bytes);
            log.debug("Published {} for case {}", event.getEventType(), caseId);
        } catch (Exception e) {
            log.warn("Could not publish case-created event for {}: {}", caseId, e.getMessage());
        }
    }

    public void publishActivity(AutomationInteractionType type, String caseId, String userId, String resultId) {
        try {
            String topic = switch (type) {
                case COMMENT -> Topics.COMMENT_EVENTS;
                case REPLY -> Topics.COMMENT_EVENTS;
                case REACTION -> Topics.REACTION_EVENTS;
                case VOTE -> Topics.VOTE_EVENTS;
            };
            Map<String, Object> payload = Map.of(
                    "eventType", "automation." + type.name().toLowerCase(),
                    "caseId", caseId,
                    "userId", userId,
                    "resultId", resultId,
                    "source", "ai-engine"
            );
            byte[] bytes = EventJson.mapper().writeValueAsBytes(payload);
            kafkaTemplate.send(topic, caseId, bytes);
            log.debug("Published automation {} for case {}", type, caseId);
        } catch (Exception e) {
            log.warn("Could not publish automation {} for case {}: {}", type, caseId, e.getMessage());
        }
    }

    public boolean isAvailable() {
        return kafkaTemplate != null;
    }
}
