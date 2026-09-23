package com.etribunal.ai.automation.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.common.kafka.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

class ModerationProducerTest {

    private KafkaTemplate<String, byte[]> kafkaTemplate;
    private ModerationProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        producer = new ModerationProducer(kafkaTemplate);
    }

    @Test
    void sendModerationRequest_sendsPayload() {
        producer.sendModerationRequest("CASE", "entity-1", "content", "user-1");

        verify(kafkaTemplate).send(eq(Topics.MODERATION_TASKS), eq("entity-1"), any(byte[].class));
    }

    @Test
    void sendModerationRequest_failure_doesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("kafka down"));

        producer.sendModerationRequest("CASE", "e", "c", "u");

        verify(kafkaTemplate).send(anyString(), anyString(), any(byte[].class));
    }

    @Test
    void sendImageModerationRequest_sendsPayload() {
        producer.sendImageModerationRequest("CASE", "entity-2", "http://img", "user-2");

        verify(kafkaTemplate).send(eq(Topics.MODERATION_TASKS), eq("entity-2"), any(byte[].class));
    }

    @Test
    void sendImageModerationRequest_failure_doesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("down"));

        producer.sendImageModerationRequest("CASE", "e", "url", "u");

        verify(kafkaTemplate).send(anyString(), anyString(), any(byte[].class));
    }
}
