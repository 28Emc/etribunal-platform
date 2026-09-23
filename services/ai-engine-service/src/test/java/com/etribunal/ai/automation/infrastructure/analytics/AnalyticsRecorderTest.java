package com.etribunal.ai.automation.infrastructure.analytics;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.domain.AutomationInteractionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

class AnalyticsRecorderTest {

    private JdbcTemplate jdbcTemplate;
    private AnalyticsRecorder recorder;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        recorder = new AnalyticsRecorder(jdbcTemplate);
    }

    @Test
    void recordInteraction_vote_insertsWithVoteAction() {
        String caseId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        recorder.recordInteraction(AutomationInteractionType.VOTE, caseId, userId, "r1");

        verify(jdbcTemplate).update(anyString(), eq("VOTE"), any(UUID.class), any(UUID.class), eq("r1"));
    }

    @Test
    void recordInteraction_comment_insertsWithCommentAction() {
        recorder.recordInteraction(AutomationInteractionType.COMMENT,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "r2");

        verify(jdbcTemplate).update(anyString(), eq("COMMENT"), any(UUID.class), any(UUID.class), eq("r2"));
    }

    @Test
    void recordInteraction_reply_insertsWithCommentAction() {
        recorder.recordInteraction(AutomationInteractionType.REPLY,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "r3");

        verify(jdbcTemplate).update(anyString(), eq("COMMENT"), any(UUID.class), any(UUID.class), eq("r3"));
    }

    @Test
    void recordInteraction_reaction_insertsWithReactionAction() {
        recorder.recordInteraction(AutomationInteractionType.REACTION,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "r4");

        verify(jdbcTemplate).update(anyString(), eq("REACTION"), any(UUID.class), any(UUID.class), eq("r4"));
    }

    @Test
    void recordInteraction_nullIds_passesNullUuids() {
        recorder.recordInteraction(AutomationInteractionType.COMMENT, null, null, "r5");

        verify(jdbcTemplate).update(anyString(), eq("COMMENT"), isNull(), isNull(), eq("r5"));
    }

    @Test
    void recordInteraction_blankIds_passesNullUuids() {
        recorder.recordInteraction(AutomationInteractionType.VOTE, "", "  ", "r6");

        verify(jdbcTemplate).update(anyString(), eq("VOTE"), isNull(), isNull(), eq("r6"));
    }

    @Test
    void recordInteraction_invalidUuids_passesNull() {
        recorder.recordInteraction(AutomationInteractionType.COMMENT, "not-uuid", "also-bad", "r7");

        verify(jdbcTemplate).update(anyString(), eq("COMMENT"), isNull(), isNull(), eq("r7"));
    }

    @Test
    void recordInteraction_dbFailure_doesNotThrow() {
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        recorder.recordInteraction(AutomationInteractionType.COMMENT,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "r8");

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
    }
}
