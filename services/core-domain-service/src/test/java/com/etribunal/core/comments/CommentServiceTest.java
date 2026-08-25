package com.etribunal.core.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.cases.CaseType;
import com.etribunal.core.notifications.NotificationService;
import com.etribunal.core.notifications.NotificationType;
import com.etribunal.core.reactions.ReactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private com.etribunal.core.users.InternalUsersClient usersClient;

    @Mock
    private NotificationService notificationService;

    private CommentService commentService;

    private final UUID caseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, caseRepository,
                reactionRepository, usersClient, notificationService);
        lenient().when(caseRepository.findById(caseId))
                .thenReturn(Optional.of(publicCase()));
    }

    @Test
    void createCommentOnPublicCaseIncrementsCounter() {
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        commentService.createComment(caseId, userId, "  Mi comentario  ", null,
                false);

        ArgumentCaptor<CommentEntity> captor =
                ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Mi comentario");
        assertThat(captor.getValue().getCaseId()).isEqualTo(caseId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().isAnonymous()).isFalse();
        verify(caseRepository).adjustCommentCounter(caseId, 1);
    }

    @Test
    void createCommentOnWaitingCaseIsRejected() {
        lenient().when(caseRepository.findById(caseId))
                .thenReturn(Optional.of(publicCase(CaseStatus.WAITING)));

        assertThatThrownBy(() -> commentService.createComment(caseId, userId,
                "contenido", null, false))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void replyToTopLevelCommentIsAllowed() {
        CommentEntity parent = comment(UUID.randomUUID(), userId, null);
        when(commentRepository.findById(parent.getId()))
                .thenReturn(Optional.of(parent));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        commentService.createComment(caseId, userId, "respuesta",
                parent.getId(), false);

        ArgumentCaptor<CommentEntity> captor =
                ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(parent.getId());
    }

    @Test
    void replyToReplyIsRejectedAtMaxDepth() {
        UUID rootId = UUID.randomUUID();
        CommentEntity level1 = comment(UUID.randomUUID(), userId, rootId);
        when(commentRepository.findById(level1.getId()))
                .thenReturn(Optional.of(level1));
        when(commentRepository.findById(rootId)).thenReturn(Optional.of(
                comment(rootId, userId, null)));

        assertThatThrownBy(() -> commentService.createComment(caseId, userId,
                "respuesta a respuesta", level1.getId(), false))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("profundidad");
    }

    @Test
    void parentFromAnotherCaseIsRejected() {
        CommentEntity foreign = new CommentEntity();
        foreign.setCaseId(UUID.randomUUID());
        foreign.setUserId(userId);
        foreign.setContent("contenido");
        setField(foreign, "id", UUID.randomUUID());
        when(commentRepository.findById(foreign.getId()))
                .thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> commentService.createComment(caseId, userId,
                "respuesta", foreign.getId(), false))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("padre");
    }

    @Test
    void deleteByOwnerRemovesRepliesAndDecrementsCounter() {
        CommentEntity comment = comment(UUID.randomUUID(), userId, null);
        CommentEntity reply = comment(UUID.randomUUID(), userId,
                comment.getId());
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId()))
                .thenReturn(Optional.of(comment));
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(comment.getId()))
                .thenReturn(List.of(reply));

        commentService.deleteComment(comment.getId(), userId);

        verify(commentRepository).delete(comment);
        verify(caseRepository).adjustCommentCounter(caseId, -2);
    }

    @Test
    void deleteByNonOwnerIsForbidden() {
        CommentEntity comment = comment(UUID.randomUUID(), userId, null);
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId()))
                .thenReturn(Optional.of(comment));
        UUID intruder = UUID.randomUUID();

        assertThatThrownBy(() -> commentService.deleteComment(comment.getId(),
                intruder))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void cursorPaginationClampsLimitAndDetectsHasMore() {
        List<CommentEntity> fetched = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            CommentEntity c = comment(UUID.randomUUID(), userId, null);
            setField(c, "createdAt", now.minusSeconds(i * 60L));
            setField(c, "updatedAt", now.minusSeconds(i * 60L));
            fetched.add(c);
        }
        when(commentRepository
                .findByCaseIdAndParentIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        any(), any()))
                .thenReturn(fetched);
        when(commentRepository
                .findByParentIdInAndDeletedAtIsNullOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());

        var page = commentService.getCommentsCursor(caseId, null, null, 3);

        assertThat(page.data()).hasSize(3);
        assertThat(page.has_more()).isTrue();
        assertThat(page.next_cursor())
                .isEqualTo(now.minusSeconds(120).toString());
    }

    @Test
    void anonymousAuthorIsMaskedInTheResponse() {
        CommentEntity comment = comment(UUID.randomUUID(), userId, null);
        comment.setAnonymous(true);
        when(commentRepository
                .findByCaseIdAndParentIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        any(), any()))
                .thenReturn(List.of(comment));
        when(commentRepository
                .findByParentIdInAndDeletedAtIsNullOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(usersClient.summaries(any())).thenReturn(List.of(new
                com.etribunal.core.users.UserSummary(userId, "realuser",
                        "https://example.com/a.png", true)));

        var page = commentService.getCommentsCursor(caseId, null, null, 20);

        assertThat(page.data()).hasSize(1);
        assertThat(page.data().get(0).user().username())
                .isEqualTo(com.etribunal.core.cases.CaseService.MASKED_USERNAME);
        assertThat(page.data().get(0).is_anonymous()).isTrue();
    }

    private CaseEntity publicCase() {
        return publicCase(CaseStatus.PUBLIC);
    }

    private CaseEntity publicCase(CaseStatus status) {
        CaseEntity entity = new CaseEntity();
        entity.setType(com.etribunal.core.cases.CaseType.classic);
        entity.setTitle("Caso");
        entity.setSideAContent("A");
        entity.setSideAUserId(userId);
        entity.setStatus(status);
        return entity;
    }

    private CommentEntity comment(UUID id, UUID author, UUID parentId) {
        CommentEntity entity = new CommentEntity();
        entity.setCaseId(caseId);
        entity.setUserId(author);
        entity.setParentId(parentId);
        entity.setContent("contenido");
        entity.setAnonymous(false);
        setField(entity, "id", id);
        setField(entity, "createdAt", now);
        setField(entity, "updatedAt", now);
        return entity;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
