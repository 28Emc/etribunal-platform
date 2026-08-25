package com.etribunal.core.reactions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReactionRepository extends JpaRepository<ReactionEntity, UUID> {

    List<ReactionEntity> findByTargetTypeAndTargetId(ReactionTarget targetType,
                                                     UUID targetId);

    Optional<ReactionEntity> findFirstByTargetTypeAndTargetIdAndUserId(
            ReactionTarget targetType, UUID targetId, UUID userId);

    Optional<ReactionEntity> findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
            ReactionTarget targetType, UUID targetId, UUID userId, Emoji emoji);

    long countByCommentIdIn(List<UUID> commentIds);

    @Query("select r.commentId as commentId, count(r) as total "
            + "from ReactionEntity r where r.commentId in :ids group by r.commentId")
    List<CommentReactionCount> countByCommentIdGrouped(@Param("ids") List<UUID> ids);

    interface CommentReactionCount {
        UUID getCommentId();

        long getTotal();
    }
}
