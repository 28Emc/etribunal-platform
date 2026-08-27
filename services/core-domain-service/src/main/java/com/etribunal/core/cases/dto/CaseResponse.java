package com.etribunal.core.cases.dto;

import com.etribunal.core.users.UserSummary;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrato heredado de mapCase() del monolito (snake_case).
 */
public record CaseResponse(
        UUID id,
        String type,
        String status,
        String category,
        String title,
        String side_a_content,
        String side_b_content,
        String side_a_subtitle,
        String side_b_subtitle,
        String both_wrong_subtitle,
        String content_language,
        boolean is_anonymous,
        boolean is_private,
        Instant created_at,
        Instant updated_at,
        UUID side_a_user_id,
        UUID side_b_user_id,
        UserDto side_a_user,
        UserDto side_b_user,
        int total_votes,
        int votes_a,
        int votes_b,
        int votes_both_wrong,
        int total_comments,
        int total_views,
        int total_shares,
        int total_anchors,
        String moderation_status,
        boolean is_saved,
        boolean is_shared
) {

    public record UserDto(
            UUID id,
            String username,
            String avatar_url,
            boolean is_anonymous
    ) {
    }

    public static UserDto toUserDto(UserSummary summary) {
        if (summary == null) {
            return null;
        }
        return new UserDto(summary.id(), summary.username(), summary.avatarUrl(),
                summary.anonymous());
    }
}
