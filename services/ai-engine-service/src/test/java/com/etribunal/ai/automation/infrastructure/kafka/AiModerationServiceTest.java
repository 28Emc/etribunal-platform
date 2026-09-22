package com.etribunal.ai.automation.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiModerationServiceTest {

    @Mock
    private ModerationProducer producer;

    @Mock
    private ModerationConsumer consumer;

    @InjectMocks
    private AiModerationService service;

    @BeforeEach
    void setUp() {
    }

    @Test
    void requestTextModeration_sendsToProducer() {
        service.requestTextModeration("COMMENT", "comment-123", "Test content", "user-456");

        verify(producer).sendModerationRequest("COMMENT", "comment-123", "Test content", "user-456");
    }

    @Test
    void requestImageModeration_sendsToProducer() {
        service.requestImageModeration("CASE_IMAGE", "image-789", "https://example.com/img.jpg", "user-123");

        verify(producer).sendImageModerationRequest("CASE_IMAGE", "image-789", "https://example.com/img.jpg", "user-123");
    }

    @Test
    void getModerationStatus_returnsFromConsumer() {
        when(consumer.getModerationStatus("entity-123")).thenReturn("APPROVED");

        String status = service.getModerationStatus("entity-123");

        assertThat(status).isEqualTo("APPROVED");
        verify(consumer).getModerationStatus("entity-123");
    }

    @Test
    void isApproved_returnsTrue_whenApproved() {
        when(consumer.getModerationStatus("entity-123")).thenReturn("APPROVED");

        assertThat(service.isApproved("entity-123")).isTrue();
    }

    @Test
    void isApproved_returnsFalse_whenNotApproved() {
        when(consumer.getModerationStatus("entity-123")).thenReturn("FLAGGED");

        assertThat(service.isApproved("entity-123")).isFalse();
    }

    @Test
    void isApproved_returnsFalse_whenNull() {
        when(consumer.getModerationStatus("entity-123")).thenReturn(null);

        assertThat(service.isApproved("entity-123")).isFalse();
    }

    @Test
    void isRejected_returnsTrue_whenRejected() {
        when(consumer.getModerationStatus("entity-123")).thenReturn("REJECTED");

        assertThat(service.isRejected("entity-123")).isTrue();
    }

    @Test
    void isRejected_returnsFalse_whenNotRejected() {
        when(consumer.getModerationStatus("entity-123")).thenReturn("APPROVED");

        assertThat(service.isRejected("entity-123")).isFalse();
    }

    @Test
    void isRejected_returnsFalse_whenNull() {
        when(consumer.getModerationStatus("entity-123")).thenReturn(null);

        assertThat(service.isRejected("entity-123")).isFalse();
    }
}