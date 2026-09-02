package com.etribunal.identity.auth.dto;

import com.etribunal.identity.user.UserEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("avatar_url") String avatarUrl,
        String role,
        String status,
        @JsonProperty("created_at") Instant createdAt) {

    public static UserResponse from(UserEntity u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getUsername(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getRole(),
                u.getStatus(),
                u.getCreatedAt());
    }
}
