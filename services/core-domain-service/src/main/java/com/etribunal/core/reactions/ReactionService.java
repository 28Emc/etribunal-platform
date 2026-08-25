package com.etribunal.core.reactions;

import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.comments.CommentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReactionService {

    private static final List<Emoji> ALLOWED = List.of(Emoji.LIKE, Emoji.LOVE,
            Emoji.ANGRY);

    private final ReactionRepository reactionRepository;
    private final CaseRepository caseRepository;
    private final CommentRepository commentRepository;

    public ReactionService(ReactionRepository reactionRepository,
                           CaseRepository caseRepository,
                           CommentRepository commentRepository) {
        this.reactionRepository = reactionRepository;
        this.caseRepository = caseRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Semántica toggle heredada: mismo emoji otra vez lo quita; un emoji distinto
     * reemplaza al anterior (una sola reacción por usuario y target).
     */
    @Transactional
    public ReactionsSummary addReaction(UUID userId, ReactionTarget targetType,
                                        UUID targetId, Emoji emoji) {
        validateTarget(targetType, targetId);

        Optional<ReactionEntity> sameEmoji = reactionRepository
                .findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
                        targetType, targetId, userId, emoji);
        if (sameEmoji.isPresent()) {
            reactionRepository.delete(sameEmoji.get());
            return summary(targetType, targetId, userId);
        }

        reactionRepository.findFirstByTargetTypeAndTargetIdAndUserId(
                        targetType, targetId, userId)
                .ifPresent(reactionRepository::delete);

        ReactionEntity reaction = new ReactionEntity();
        reaction.setTargetType(targetType);
        reaction.setTargetId(targetId);
        reaction.setEmoji(emoji);
        reaction.setUserId(userId);
        if (targetType == ReactionTarget.CASE) {
            reaction.setCaseId(targetId);
        } else {
            reaction.setCommentId(targetId);
        }
        reactionRepository.save(reaction);

        return summary(targetType, targetId, userId);
    }

    @Transactional
    public ReactionsSummary removeReaction(UUID userId, ReactionTarget targetType,
                                           UUID targetId, Emoji emoji) {
        validateTarget(targetType, targetId);
        reactionRepository
                .findFirstByTargetTypeAndTargetIdAndUserIdAndEmoji(
                        targetType, targetId, userId, emoji)
                .ifPresent(reactionRepository::delete);
        return summary(targetType, targetId, userId);
    }

    @Transactional(readOnly = true)
    public ReactionsSummary getReactions(ReactionTarget targetType, UUID targetId,
                                         UUID requesterId) {
        validateTarget(targetType, targetId);
        return summary(targetType, targetId, requesterId);
    }

    private ReactionsSummary summary(ReactionTarget targetType, UUID targetId,
                                     UUID requesterId) {
        List<ReactionEntity> reactions = reactionRepository
                .findByTargetTypeAndTargetId(targetType, targetId);

        List<ReactionsSummary.EmojiCount> counts = new ArrayList<>(ALLOWED.size());
        String userReaction = null;
        for (Emoji emoji : ALLOWED) {
            long count = reactions.stream()
                    .filter(r -> r.getEmoji() == emoji).count();
            counts.add(new ReactionsSummary.EmojiCount(emoji.name(), count));
        }
        userReaction = reactions.stream()
                .filter(r -> r.getUserId().equals(requesterId))
                .map(r -> r.getEmoji().name())
                .findFirst().orElse(null);

        return new ReactionsSummary(counts, userReaction);
    }

    private void validateTarget(ReactionTarget targetType, UUID targetId) {
        boolean exists = targetType == ReactionTarget.CASE
                ? caseRepository.existsById(targetId)
                : commentRepository.existsById(targetId);
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    targetType == ReactionTarget.CASE
                            ? "Caso no encontrado" : "Comentario no encontrado");
        }
    }

    public record ReactionsSummary(List<EmojiCount> reactions, String user_reaction) {

        public record EmojiCount(String emoji, long count) {
        }
    }
}
