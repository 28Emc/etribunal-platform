package com.etribunal.ai.automation.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @Test
    void requestTextModeration_delegatesToProducer() {
        service.requestTextModeration("CASE", "case-1", "content", "user-1");

        verify(producer).sendModerationRequest("CASE", "case-1", "content", "user-1");
    }

    @Test
    void requestImageModeration_delegatesToProducer() {
        service.requestImageModeration("CASE", "case-1", "https://img.url", "user-1");

        verify(producer).sendImageModerationRequest("CASE", "case-1", "https://img.url", "user-1");
    }

    @Test
    void getModerationStatus_delegatesToConsumer() {
        when(consumer.getModerationStatus("case-1")).thenReturn("APPROVED");

        String status = service.getModerationStatus("case-1");

        assertThat(status).isEqualTo("APPROVED");
    }

    @Test
    void isApproved_returnsTrue_whenApproved() {
        when(consumer.getModerationStatus("case-1")).thenReturn("APPROVED");

        assertThat(service.isApproved("case-1")).isTrue();
    }

    @Test
    void isApproved_returnsFalse_whenPending() {
        when(consumer.getModerationStatus("case-1")).thenReturn("PENDING");

        assertThat(service.isApproved("case-1")).isFalse();
    }

    @Test
    void isRejected_returnsTrue_whenRejected() {
        when(consumer.getModerationStatus("case-1")).thenReturn("REJECTED");

        assertThat(service.isRejected("case-1")).isTrue();
    }

    @Test
    void isRejected_returnsFalse_whenApproved() {
        when(consumer.getModerationStatus("case-1")).thenReturn("APPROVED");

        assertThat(service.isRejected("case-1")).isFalse();
    }
}