package com.etribunal.ai.automation.infrastructure.kafka;

import com.etribunal.common.kafka.EventJson;
import com.etribunal.common.kafka.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ModerationProducer {

    private static final Logger log = LoggerFactory.getLogger(ModerationProducer.class);
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public ModerationProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendModerationRequest(String entityType, String entityId, String content, String userId) {
        try {
            Map<String, Object> payload = Map.of(
                    "entityType", entityType,
                    "entityId", entityId,
                    "content", content,
                    "userId", userId,
                    "requestType", "TEXT_MODERATION"
            );

            byte[] bytes = EventJson.mapper().writeValueAsBytes(payload);
            kafkaTemplate.send(Topics.MODERATION_TASKS, entityId, bytes);
            log.debug("Moderation request sent for {} {}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to send moderation request for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    public void sendImageModerationRequest(String entityType, String entityId, String imageUrl, String userId) {
        try {
            Map<String, Object> payload = Map.of(
                    "entityType", entityType,
                    "entityId", entityId,
                    "imageUrl", imageUrl,
                    "userId", userId,
                    "requestType", "IMAGE_MODERATION"
            );

            byte[] bytes = EventJson.mapper().writeValueAsBytes(payload);
            kafkaTemplate.send(Topics.MODERATION_TASKS, entityId, bytes);
            log.debug("Image moderation request sent for {} {}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to send image moderation request for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }
}