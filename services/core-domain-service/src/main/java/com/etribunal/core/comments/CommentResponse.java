package com.etribunal.core.comments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contrato heredado del monolito (snake_case).
 */
public record CommentResponse(
        UUID id,
        String content,
        UUID parent_id,
        Instant created_at,
        Instant updated_at,
        UUID case_id,
        UUID user_id,
        boolean is_anonymous,
        UserDto user,
        List<CommentResponse> replies,
        int replies_count,
        long reactions_count
) {

    public record UserDto(UUID id, String username, String avatar_url,
                          boolean is_anonymous) {
    }

    public static CommentResponse toResponse(CommentEntity entity, UserDto user,
                                             List<CommentResponse> replies,
                                             long reactionsCount) {
        return new CommentResponse(entity.getId(), entity.getContent(),
                entity.getParentId(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getCaseId(), entity.getUserId(), entity.isAnonymous(), user,
                replies, replies.size(), reactionsCount);
    }
}
