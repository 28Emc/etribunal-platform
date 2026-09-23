package com.etribunal.ai.automation.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.common.kafka.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class AutomationEventPublisherTest {

    private KafkaTemplate<String, byte[]> kafkaTemplate;
    private AutomationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher = new AutomationEventPublisher(kafkaTemplate);
    }

    @Test
    void publishCaseCreated_sendsEvent() {
        UUID caseId = UUID.randomUUID();
        UUID sideA = UUID.randomUUID();
        UUID sideB = UUID.randomUUID();

        publisher.publishCaseCreated(caseId, sideA, sideB, "classic");

        verify(kafkaTemplate).send(eq(Topics.CASE_EVENTS), eq(caseId.toString()), any(byte[].class));
    }

    @Test
    void publishCaseCreated_kafkaFailure_doesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("kafka down"));

        publisher.publishCaseCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "vote");

        verify(kafkaTemplate).send(anyString(), anyString(), any(byte[].class));
    }

    @Test
    void publishActivity_comment_usesCommentTopic() {
        publisher.publishActivity(AutomationInteractionType.COMMENT, "case-1", "user-1", "res-1");

        verify(kafkaTemplate).send(eq(Topics.COMMENT_EVENTS), eq("case-1"), any(byte[].class));
    }

    @Test
    void publishActivity_reply_usesCommentTopic() {
        publisher.publishActivity(AutomationInteractionType.REPLY, "case-1", "user-1", "res-1");

        verify(kafkaTemplate).send(eq(Topics.COMMENT_EVENTS), eq("case-1"), any(byte[].class));
    }

    @Test
    void publishActivity_reaction_usesReactionTopic() {
        publisher.publishActivity(AutomationInteractionType.REACTION, "case-1", "user-1", "res-1");

        verify(kafkaTemplate).send(eq(Topics.REACTION_EVENTS), eq("case-1"), any(byte[].class));
    }

    @Test
    void publishActivity_vote_usesVoteTopic() {
        publisher.publishActivity(AutomationInteractionType.VOTE, "case-1", "user-1", "res-1");

        verify(kafkaTemplate).send(eq(Topics.VOTE_EVENTS), eq("case-1"), any(byte[].class));
    }

    @Test
    void publishActivity_failure_doesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("down"));

        publisher.publishActivity(AutomationInteractionType.COMMENT, "c", "u", "r");

        verify(kafkaTemplate).send(anyString(), anyString(), any(byte[].class));
    }

    @Test
    void isAvailable_true_whenTemplatePresent() {
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_false_whenTemplateNull() {
        AutomationEventPublisher nullPub = new AutomationEventPublisher(null);
        assertThat(nullPub.isAvailable()).isFalse();
    }
}
