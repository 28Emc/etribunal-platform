package com.etribunal.core.reactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.comments.CommentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AnalyticsService analyticsService;

    private ReactionService reactionService;

    private final UUID caseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reactionService = new ReactionService(reactionRepository, caseRepository,
                commentRepository, analyticsService);
        lenient().when(caseRepository.existsById(caseId)).thenReturn(true);
    }

    @Test
    void newReactionIsSaved() {
        when(reactionRepository
                .findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
                        ReactionTarget.CASE, caseId, userId, Emoji.LIKE))
                .thenReturn(Optional.empty());
        when(reactionRepository.findFirstByTargetTypeAndTargetIdAndUserId(
                ReactionTarget.CASE, caseId, userId)).thenReturn(Optional.empty());
        when(reactionRepository.findByTargetTypeAndTargetId(ReactionTarget.CASE,
                caseId)).thenReturn(List.of(reaction(userId, Emoji.LIKE)));

        var summary = reactionService.addReaction(userId, ReactionTarget.CASE,
                caseId, Emoji.LIKE);

        ArgumentCaptor<ReactionEntity> captor =
                ArgumentCaptor.forClass(ReactionEntity.class);
        verify(reactionRepository).save(captor.capture());
        assertThat(captor.getValue().getEmoji()).isEqualTo(Emoji.LIKE);
        assertThat(captor.getValue().getCaseId()).contains(caseId);
        assertThat(summary.user_reaction()).isEqualTo("LIKE");
    }

    @Test
    void sameEmojiAgainRemovesIt() {
        ReactionEntity existing = new ReactionEntity();
        existing.setEmoji(Emoji.LIKE);
        existing.setUserId(userId);
        when(reactionRepository
                .findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
                        ReactionTarget.CASE, caseId, userId, Emoji.LIKE))
                .thenReturn(Optional.of(existing));
        when(reactionRepository.findByTargetTypeAndTargetId(ReactionTarget.CASE,
                caseId)).thenReturn(List.of());

        var summary = reactionService.addReaction(userId, ReactionTarget.CASE,
                caseId, Emoji.LIKE);

        verify(reactionRepository).delete(existing);
        verify(reactionRepository, never()).save(any());
        assertThat(summary.user_reaction()).isNull();
    }

    @Test
    void differentEmojiReplacesPreviousOne() {
        ReactionEntity old = new ReactionEntity();
        old.setEmoji(Emoji.LIKE);
        old.setUserId(userId);
        when(reactionRepository
                .findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
                        ReactionTarget.CASE, caseId, userId, Emoji.ANGRY))
                .thenReturn(Optional.empty());
        when(reactionRepository.findFirstByTargetTypeAndTargetIdAndUserId(
                ReactionTarget.CASE, caseId, userId)).thenReturn(Optional.of(old));
        ReactionEntity saved = new ReactionEntity();
        saved.setEmoji(Emoji.ANGRY);
        saved.setUserId(userId);
        when(reactionRepository.findByTargetTypeAndTargetId(ReactionTarget.CASE,
                caseId)).thenReturn(List.of(saved));

        var summary = reactionService.addReaction(userId, ReactionTarget.CASE,
                caseId, Emoji.ANGRY);

        verify(reactionRepository).delete(old);
        verify(reactionRepository).save(any());
        assertThat(summary.user_reaction()).isEqualTo("ANGRY");
    }

    @Test
    void removeReactionDeletesMatchingEmoji() {
        ReactionEntity existing = new ReactionEntity();
        existing.setEmoji(Emoji.LOVE);
        existing.setUserId(userId);
        when(reactionRepository
                .findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
                        ReactionTarget.CASE, caseId, userId, Emoji.LOVE))
                .thenReturn(Optional.of(existing));
        when(reactionRepository.findByTargetTypeAndTargetId(ReactionTarget.CASE,
                caseId)).thenReturn(List.of());

        var summary = reactionService.removeReaction(userId, ReactionTarget.CASE,
                caseId, Emoji.LOVE);

        verify(reactionRepository).delete(existing);
        assertThat(summary.user_reaction()).isNull();
    }

    @Test
    void summaryAggregatesCountsPerEmoji() {
        when(caseRepository.existsById(caseId)).thenReturn(true);
        List<ReactionEntity> reactions = List.of(
                reaction(userId, Emoji.LIKE),
                reaction(UUID.randomUUID(), Emoji.LIKE),
                reaction(UUID.randomUUID(), Emoji.ANGRY));
        when(reactionRepository.findByTargetTypeAndTargetId(ReactionTarget.CASE,
                caseId)).thenReturn(reactions);

        var summary = reactionService.getReactions(ReactionTarget.CASE, caseId,
                userId);

        assertThat(summary.reactions())
                .anySatisfy(r -> {
                    assertThat(r.emoji()).isEqualTo("LIKE");
                    assertThat(r.count()).isEqualTo(2);
                })
                .anySatisfy(r -> {
                    assertThat(r.emoji()).isEqualTo("ANGRY");
                    assertThat(r.count()).isEqualTo(1);
                })
                .anySatisfy(r -> {
                    assertThat(r.emoji()).isEqualTo("LOVE");
                    assertThat(r.count()).isEqualTo(0);
                });
        assertThat(summary.user_reaction()).isEqualTo("LIKE");
    }

    @Test
    void unknownCaseIsRejected() {
        UUID missing = UUID.randomUUID();
        when(caseRepository.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> reactionService.addReaction(userId,
                ReactionTarget.CASE, missing, Emoji.LIKE))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void unknownCommentIsRejected() {
        UUID missing = UUID.randomUUID();
        when(commentRepository.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> reactionService.addReaction(userId,
                ReactionTarget.COMMENT, missing, Emoji.LOVE))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private ReactionEntity reaction(UUID author, Emoji emoji) {
        ReactionEntity entity = new ReactionEntity();
        entity.setTargetType(ReactionTarget.CASE);
        entity.setTargetId(caseId);
        entity.setUserId(author);
        entity.setEmoji(emoji);
        return entity;
    }
}
