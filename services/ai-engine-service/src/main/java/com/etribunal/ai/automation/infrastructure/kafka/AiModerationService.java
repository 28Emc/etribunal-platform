package com.etribunal.ai.automation.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AiModerationService {

    private static final Logger log = LoggerFactory.getLogger(AiModerationService.class);

    private final ModerationProducer producer;
    private final ModerationConsumer consumer;

    public AiModerationService(ModerationProducer producer, ModerationConsumer consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }

    public void requestTextModeration(String entityType, String entityId, String content, String userId) {
        producer.sendModerationRequest(entityType, entityId, content, userId);
        log.debug("Text moderation requested for {} {}", entityType, entityId);
    }

    public void requestImageModeration(String entityType, String entityId, String imageUrl, String userId) {
        producer.sendImageModerationRequest(entityType, entityId, imageUrl, userId);
        log.debug("Image moderation requested for {} {}", entityType, entityId);
    }

    public String getModerationStatus(String entityId) {
        return consumer.getModerationStatus(entityId);
    }

    public boolean isApproved(String entityId) {
        String status = consumer.getModerationStatus(entityId);
        return "APPROVED".equals(status);
    }

    public boolean isRejected(String entityId) {
        String status = consumer.getModerationStatus(entityId);
        return "REJECTED".equals(status);
    }
}