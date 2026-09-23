package com.etribunal.ai.automation.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ModerationConsumerTest {

    private ModerationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ModerationConsumer();
    }

    @Test
    void onModerationResult_parsesAndStores() {
        String json = "{\"entityId\":\"e1\",\"status\":\"APPROVED\"}";

        consumer.onModerationResult(json.getBytes(StandardCharsets.UTF_8));

        assertThat(consumer.getModerationStatus("e1")).isEqualTo("APPROVED");
    }

    @Test
    void onModerationResult_missingFields_defaults() {
        String json = "{\"other\":\"value\"}";

        consumer.onModerationResult(json.getBytes(StandardCharsets.UTF_8));

        assertThat(consumer.getModerationStatus("unknown")).isEqualTo("PENDING");
    }

    @Test
    void getModerationStatus_unknownEntity_returnsPending() {
        assertThat(consumer.getModerationStatus("missing")).isEqualTo("PENDING");
    }

    @Test
    void clearResult_removesEntry() {
        consumer.onModerationResult("{\"entityId\":\"e2\",\"status\":\"REJECTED\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(consumer.getModerationStatus("e2")).isEqualTo("REJECTED");

        consumer.clearResult("e2");

        assertThat(consumer.getModerationStatus("e2")).isEqualTo("PENDING");
    }

    @Test
    void onModerationResult_invalidJson_doesNotThrow() {
        consumer.onModerationResult("not-json".getBytes(StandardCharsets.UTF_8));

        assertThat(consumer.getModerationStatus("x")).isEqualTo("PENDING");
    }

    @Test
    void onModerationResult_statusPending_default() {
        consumer.onModerationResult("{\"entityId\":\"e3\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(consumer.getModerationStatus("e3")).isEqualTo("PENDING");
    }
}
