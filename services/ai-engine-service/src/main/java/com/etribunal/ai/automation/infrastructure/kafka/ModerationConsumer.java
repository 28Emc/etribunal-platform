package com.etribunal.ai.automation.infrastructure.kafka;

import com.etribunal.common.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModerationConsumer.class);

    private final Map<String, String> moderationResults = new ConcurrentHashMap<>();

    @KafkaListener(
            topics = Topics.MODERATION_TASKS,
            groupId = "ai-engine-moderation",
            properties = {
                "spring.json.value.default.type=java.util.Map",
                "specific.class=[Ljava.lang.Object;"
            }
    )
    public void onModerationResult(byte[] message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = com.etribunal.common.kafka.EventJson.mapper()
                    .readValue(message, Map.class);

            String entityId = (String) result.getOrDefault("entityId", "unknown");
            String status = (String) result.getOrDefault("status", "PENDING");

            moderationResults.put(entityId, status);
            log.debug("Moderation result for {}: {}", entityId, status);
        } catch (Exception e) {
            log.error("Failed to parse moderation result: {}", e.getMessage());
        }
    }

    public String getModerationStatus(String entityId) {
        return moderationResults.getOrDefault(entityId, "PENDING");
    }

    public void clearResult(String entityId) {
        moderationResults.remove(entityId);
    }
}