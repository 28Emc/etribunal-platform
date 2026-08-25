package com.etribunal.core.reactions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "reactions")
public class ReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, columnDefinition = "varchar(10)")
    private ReactionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "emoji", nullable = false, columnDefinition = "varchar(10)")
    private Emoji emoji;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ReactionTarget getTargetType() {
        return targetType;
    }

    public void setTargetType(ReactionTarget targetType) {
        this.targetType = targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public Emoji getEmoji() {
        return emoji;
    }

    public void setEmoji(Emoji emoji) {
        this.emoji = emoji;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Optional<UUID> getCaseId() {
        return Optional.ofNullable(caseId);
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public Optional<UUID> getCommentId() {
        return Optional.ofNullable(commentId);
    }

    public void setCommentId(UUID commentId) {
        this.commentId = commentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
