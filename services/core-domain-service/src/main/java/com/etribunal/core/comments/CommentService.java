package com.etribunal.core.comments;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.notifications.NotificationService;
import com.etribunal.common.domain.notification.NotificationType;
import com.etribunal.core.reactions.ReactionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentService {

    private static final int MAX_DEPTH = 2;

    private final CommentRepository commentRepository;
    private final CaseRepository caseRepository;
    private final ReactionRepository reactionRepository;
    private final com.etribunal.core.users.InternalUsersClient usersClient;
    private final NotificationService notificationService;

    public CommentService(CommentRepository commentRepository,
                          CaseRepository caseRepository,
                          ReactionRepository reactionRepository,
                          com.etribunal.core.users.InternalUsersClient usersClient,
                          NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.caseRepository = caseRepository;
        this.reactionRepository = reactionRepository;
        this.usersClient = usersClient;
        this.notificationService = notificationService;
    }

    /**
     * Paginación por cursor heredada: top-level desc, take limit+1 para
     * hasMore, respuestas en batch asc.
     */
    @Transactional(readOnly = true)
    public CommentPage getCommentsCursor(UUID caseId, String before, String after,
                                         Integer limit) {
        requireCase(caseId);
        int pageSize = Math.min(Math.max(limit != null ? limit : 20, 1), 100);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<CommentEntity> top;
        Instant beforeDate = parseDate(before, false);
        if (beforeDate != null) {
            top = commentRepository
                    .findByCaseIdAndParentIdIsNullAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(
                            caseId, beforeDate, pageable);
        } else {
            Instant afterDate = parseDate(after, true);
            top = commentRepository
                    .findByCaseIdAndParentIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                            caseId, pageable);
            if (afterDate != null) {
                top = top.stream()
                        .filter(c -> c.getCreatedAt().isAfter(afterDate))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        boolean hasMore = top.size() > pageSize;
        List<CommentEntity> page = hasMore ? top.subList(0, pageSize) : top;
        String nextCursor = hasMore && !page.isEmpty()
                ? page.get(page.size() - 1).getCreatedAt().toString() : null;

        return new CommentPage(toResponses(page), nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public long getNewCommentsCount(UUID caseId, String since) {
        requireCase(caseId);
        Instant sinceDate = parseDate(since, true);
        if (sinceDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parámetro \"since\" requerido");
        }
        return commentRepository
                .countByCaseIdAndParentIdIsNullAndDeletedAtIsNullAndCreatedAtAfter(
                        caseId, sinceDate);
    }

    @Transactional
    public CommentResponse createComment(UUID caseId, UUID userId, String content,
                                         UUID parentId, boolean anonymous) {
        CaseEntity caseEntity = requireCase(caseId);
        if (caseEntity.getStatus() != CaseStatus.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes comentar en un caso que no está activo");
        }

        if (parentId != null) {
            CommentEntity parent = commentRepository.findById(parentId)
                    .filter(p -> p.getDeletedAt() == null)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Comentario padre no encontrado"));
            if (!parent.getCaseId().equals(caseId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Comentario padre no encontrado");
            }
            assertDepthAllowed(parent);
        }

        CommentEntity comment = new CommentEntity();
        comment.setCaseId(caseId);
        comment.setUserId(userId);
        comment.setContent(content.trim());
        comment.setParentId(parentId);
        comment.setAnonymous(anonymous);
        CommentEntity saved = commentRepository.save(comment);

        caseRepository.adjustCommentCounter(caseId, 1);

        // Notificaciones heredadas
        CaseEntity c = requireCase(caseId);
        notifyCommentCreated(c, saved, parentId, userId);

        return CommentResponse.toResponse(saved, maskedUser(null), List.of(), 0);
    }

    private void notifyCommentCreated(CaseEntity c, CommentEntity saved,
                                      UUID parentId, UUID actorId) {
        // Notificar a Side A
        if (c.getSideAUserId() != null && !c.getSideAUserId().equals(actorId)) {
            notificationService.createNotification(c.getSideAUserId(), actorId,
                    NotificationType.NEW_COMMENT,
                    Map.of("case_id", c.getId().toString(),
                           "case_title", c.getTitle(),
                           "comment_id", saved.getId().toString(),
                           "actor_id", actorId.toString()));
        }
        // Notificar a Side B
        if (c.getSideBUserId() != null && !c.getSideBUserId().equals(actorId)) {
            notificationService.createNotification(c.getSideBUserId(), actorId,
                    NotificationType.NEW_COMMENT,
                    Map.of("case_id", c.getId().toString(),
                           "case_title", c.getTitle(),
                           "comment_id", saved.getId().toString(),
                           "actor_id", actorId.toString()));
        }
        // Si es respuesta, notificar al autor del comentario padre
        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(parent -> {
                if (!parent.getUserId().equals(actorId)) {
                    notificationService.createNotification(parent.getUserId(), actorId,
                            NotificationType.NEW_COMMENT,
                            Map.of("case_id", c.getId().toString(),
                                   "case_title", c.getTitle(),
                                   "comment_id", saved.getId().toString(),
                                   "actor_id", actorId.toString()));
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getReplies(UUID parentId) {
        requireComment(parentId);

        List<CommentEntity> replies =
                commentRepository.findByParentIdOrderByCreatedAtAsc(parentId);
        Map<UUID, Long> reactionCounts = reactionCountMap(
                replies.stream().map(CommentEntity::getId).toList());
        LinkedHashSet<UUID> userIds = replies.stream().map(CommentEntity::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, com.etribunal.core.users.UserSummary> summaries = fetchSummaries(userIds);

        return replies.stream()
                .map(r -> buildResponse(r, List.of(), reactionCounts, summaries))
                .toList();
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        CommentEntity comment = requireComment(commentId);

        if (!comment.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes eliminar un comentario que no es tuyo");
        }

        int replyCount =
                commentRepository.findByParentIdOrderByCreatedAtAsc(commentId).size();

        // La FK ON DELETE CASCADE elimina respuestas y sus reacciones a nivel BD
        commentRepository.delete(comment);

        caseRepository.adjustCommentCounter(comment.getCaseId(), -(1 + replyCount));
    }

    private void assertDepthAllowed(CommentEntity parent) {
        int depth = 1;
        UUID cursor = parent.getParentId();
        while (cursor != null && depth < 10) {
            depth++;
            Optional<CommentEntity> ancestor = commentRepository.findById(cursor);
            if (ancestor.isEmpty()) {
                break;
            }
            cursor = ancestor.get().getParentId();
        }
        if (depth >= MAX_DEPTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Has alcanzado la profundidad máxima de respuestas");
        }
    }

    private List<CommentResponse> toResponses(List<CommentEntity> topLevel) {
        List<UUID> topIds = topLevel.stream().map(CommentEntity::getId).toList();

        List<CommentEntity> allReplies = topIds.isEmpty() ? List.of()
                : commentRepository.findByParentIdInAndDeletedAtIsNullOrderByCreatedAtAsc(
                        topIds);

        Map<UUID, List<CommentEntity>> repliesByParent = allReplies.stream()
                .collect(Collectors.groupingBy(CommentEntity::getParentId));

        List<UUID> idsWithReactions = new ArrayList<>(topIds);
        for (CommentEntity r : allReplies) {
            idsWithReactions.add(r.getId());
        }
        Map<UUID, Long> reactionCounts = reactionCountMap(idsWithReactions);

        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        for (CommentEntity c : topLevel) {
            userIds.add(c.getUserId());
        }
        for (CommentEntity r : allReplies) {
            userIds.add(r.getUserId());
        }
        Map<UUID, com.etribunal.core.users.UserSummary> summaries = fetchSummaries(userIds);

        List<CommentResponse> result = new ArrayList<>(topLevel.size());
        for (CommentEntity top : topLevel) {
            List<CommentResponse> replyDtos = repliesByParent
                    .getOrDefault(top.getId(), List.of()).stream()
                    .map(r -> buildResponse(r, List.of(), reactionCounts, summaries))
                    .toList();
            result.add(buildResponse(top, replyDtos, reactionCounts, summaries));
        }
        return result;
    }

    private CommentResponse buildResponse(CommentEntity entity,
                                          List<CommentResponse> replies,
                                          Map<UUID, Long> reactionCounts,
                                          Map<UUID, com.etribunal.core.users.UserSummary> summaries) {
        return new CommentResponse(
                entity.getId(),
                entity.getContent(),
                entity.getParentId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCaseId(),
                entity.getUserId(),
                entity.isAnonymous(),
                maskedUser(summaries.get(entity.getUserId())),
                replies,
                replies.size(),
                reactionCounts.getOrDefault(entity.getId(), 0L));
    }

    private static com.etribunal.core.comments.CommentResponse.UserDto maskedUser(
            com.etribunal.core.users.UserSummary summary) {
        if (summary == null) {
            return new com.etribunal.core.comments.CommentResponse.UserDto(null,
                    "Unknown", null, true);
        }
        if (summary.anonymous()) {
            return new com.etribunal.core.comments.CommentResponse.UserDto(summary.id(),
                    com.etribunal.core.cases.CaseService.MASKED_USERNAME,
                    com.etribunal.core.cases.CaseService.MASKED_AVATAR, true);
        }
        return new com.etribunal.core.comments.CommentResponse.UserDto(summary.id(),
                summary.username(), summary.avatarUrl(), false);
    }

    private Map<UUID, Long> reactionCountMap(List<UUID> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return reactionRepository.countByCommentIdGrouped(commentIds).stream()
                .collect(Collectors.toMap(
                        com.etribunal.core.reactions.ReactionRepository.CommentReactionCount::getCommentId,
                        com.etribunal.core.reactions.ReactionRepository.CommentReactionCount::getTotal));
    }

    private Map<UUID, com.etribunal.core.users.UserSummary> fetchSummaries(
            LinkedHashSet<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return usersClient.summaries(List.copyOf(userIds)).stream()
                .collect(Collectors.toMap(
                        com.etribunal.core.users.UserSummary::id, Function.identity()));
    }

    private CaseEntity requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));
    }

    private CommentEntity requireComment(UUID commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Comentario no encontrado"));
    }

    private static Instant parseDate(String value, boolean allowBlank) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fecha inválida: \"" + value + "\"");
        }
    }

    public record CommentPage(List<CommentResponse> data, String next_cursor,
                              boolean has_more) {
    }
}
