package com.etribunal.core.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.notifications.NotificationService;
import com.etribunal.core.moderation.ModerationService;
import com.etribunal.core.reactions.ReactionRepository;
import com.etribunal.core.users.InternalUsersClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CommentServiceAdditionalTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private InternalUsersClient usersClient;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ModerationService moderationService;

    @Mock
    private AnalyticsService analyticsService;

    private CommentService commentService;

    private final UUID caseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID replyUserId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, caseRepository,
                reactionRepository, usersClient, notificationService, moderationService,
                analyticsService);
        lenient().when(caseRepository.findById(caseId))
                .thenReturn(Optional.of(publicCase()));
    }

    @Test
    void getNewCommentsCountReturnsCountSinceDate() {
        when(commentRepository
                .countByCaseIdAndParentIdIsNullAndDeletedAtIsNullAndCreatedAtAfter(caseId, now))
                .thenReturn(3L);

        assertThat(commentService.getNewCommentsCount(caseId, now.toString())).isEqualTo(3L);
    }

    @Test
    void getNewCommentsCountThrowsOnMissingSince() {
        assertThatThrownBy(() -> commentService.getNewCommentsCount(caseId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Parámetro");
    }

    @Test
    void getNewCommentsCountThrowsOnInvalidDate() {
        assertThatThrownBy(() -> commentService.getNewCommentsCount(caseId, "no-fecha"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Fecha inválida");
    }

    @Test
    void getRepliesReturnsMappedResponses() {
        UUID parentId = UUID.randomUUID();
        CommentEntity reply1 = comment(parentId, replyUserId, parentId);
        CommentEntity reply2 = comment(parentId, replyUserId, parentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(comment(UUID.randomUUID(), userId, null)));
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(parentId))
                .thenReturn(List.of(reply1, reply2));
        when(reactionRepository.countByCommentIdGrouped(anyList()))
                .thenReturn(List.of());
        when(usersClient.summaries(anyList())).thenReturn(List.of(
                new com.etribunal.core.users.UserSummary(replyUserId, "reply_user", "https://x.com/a.png", false)));

        var replies = commentService.getReplies(parentId);

        assertThat(replies).hasSize(2);
        assertThat(replies.get(0).content()).isEqualTo("contenido");
    }

    @Test
    void updateCommentByOwnerUpdatesContent() {
        UUID commentId = UUID.randomUUID();
        CommentEntity existing = comment(commentId, userId, null);
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(existing));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usersClient.summaries(anyList())).thenReturn(List.of(
                new com.etribunal.core.users.UserSummary(userId, "author", "https://x.com/a.png", false)));
        when(reactionRepository.countByCommentIdGrouped(anyList()))
                .thenReturn(List.of());

        var response = commentService.updateComment(commentId, userId, "  nuevo contenido  ");

        assertThat(response.content()).isEqualTo("nuevo contenido");
    }

    @Test
    void updateCommentByNonOwnerThrowsForbidden() {
        UUID commentId = UUID.randomUUID();
        CommentEntity existing = comment(commentId, userId, null);
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> commentService.updateComment(commentId, UUID.randomUUID(), "contenido"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void getCommentsCursorHandlesCompositeBeforeCursor() {
        UUID lastId = UUID.randomUUID();
        Instant beforeDate = now.minusSeconds(120);
        when(commentRepository.findTopLevelBeforeCursor(eq(caseId), eq(beforeDate),
                eq(lastId), any()))
                .thenReturn(List.of());

        commentService.getCommentsCursor(caseId,
                beforeDate.toString() + "|" + lastId, null, 20);

        verify(commentRepository).findTopLevelBeforeCursor(
                eq(caseId), eq(beforeDate), eq(lastId), any());
    }

    @Test
    void getCommentsCursorAcceptsDateOnlyBefore() {
        Instant beforeDate = now.minusSeconds(120);
        when(commentRepository.findTopLevelBeforeCursor(eq(caseId), eq(beforeDate),
                eq(null), any()))
                .thenReturn(List.of());

        commentService.getCommentsCursor(caseId, beforeDate.toString(), null, 20);

        verify(commentRepository).findTopLevelBeforeCursor(
                eq(caseId), eq(beforeDate), eq(null), any());
    }

    @Test
    void reactionCountMapReturnsEmptyForEmptyList() {
        Map<UUID, Long> counts = CommentServiceTestHelper.reactionCountMap(commentService, reactionRepository, List.of());
        assertThat(counts).isEmpty();
    }

    @Test
    void reactionCountMapDelegatesToRepository() {
        com.etribunal.core.reactions.ReactionRepository.CommentReactionCount crc =
                mock(com.etribunal.core.reactions.ReactionRepository.CommentReactionCount.class);
        UUID cid = UUID.randomUUID();
        when(crc.getCommentId()).thenReturn(cid);
        when(crc.getTotal()).thenReturn(5L);
        when(reactionRepository.countByCommentIdGrouped(List.of(cid))).thenReturn(List.of(crc));

        Map<UUID, Long> counts = CommentServiceTestHelper.reactionCountMap(commentService, reactionRepository, List.of(cid));
        assertThat(counts.get(cid)).isEqualTo(5L);
    }

    @Test
    void fetchSummariesReturnsEmptyForEmptySet() {
        Map<UUID, com.etribunal.core.users.UserSummary> summaries =
                CommentServiceTestHelper.fetchSummaries(commentService, usersClient, new LinkedHashSet<>());
        assertThat(summaries).isEmpty();
    }

    @Test
    void fetchSummariesCallsClientAndMaps() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(List.of(id1, id2));
        when(usersClient.summaries(List.copyOf(ids))).thenReturn(List.of(
                new com.etribunal.core.users.UserSummary(id1, "user1", "https://x.com/1.png", false),
                new com.etribunal.core.users.UserSummary(id2, "user2", "https://x.com/2.png", false)));

        Map<UUID, com.etribunal.core.users.UserSummary> summaries =
                CommentServiceTestHelper.fetchSummaries(commentService, usersClient, ids);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(id1).username()).isEqualTo("user1");
    }

    private CaseEntity publicCase() {
        CaseEntity entity = new CaseEntity();
        entity.setType(com.etribunal.core.cases.CaseType.classic);
        entity.setTitle("Caso");
        entity.setSideAContent("A");
        entity.setStatus(CaseStatus.PUBLIC);
        entity.setSideAUserId(userId);
        return entity;
    }

    private CommentEntity comment(UUID id, UUID author, UUID parentId) {
        CommentEntity entity = new CommentEntity();
        entity.setCaseId(caseId);
        entity.setUserId(author);
        entity.setParentId(parentId);
        entity.setContent("contenido");
        entity.setAnonymous(false);
        try {
            var field = CommentEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            field = CommentEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, now);
            field = CommentEntity.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(entity, now);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    // Helper class to access private methods via reflection
    static class CommentServiceTestHelper {
        static Map<UUID, Long> reactionCountMap(CommentService service, ReactionRepository repo, List<UUID> ids) {
            try {
                var method = CommentService.class.getDeclaredMethod("reactionCountMap", List.class);
                method.setAccessible(true);
                return (Map<UUID, Long>) method.invoke(service, ids);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static Map<UUID, com.etribunal.core.users.UserSummary> fetchSummaries(
                CommentService service, InternalUsersClient client, LinkedHashSet<UUID> ids) {
            try {
                var method = CommentService.class.getDeclaredMethod("fetchSummaries", LinkedHashSet.class);
                method.setAccessible(true);
                return (Map<UUID, com.etribunal.core.users.UserSummary>) method.invoke(service, ids);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}