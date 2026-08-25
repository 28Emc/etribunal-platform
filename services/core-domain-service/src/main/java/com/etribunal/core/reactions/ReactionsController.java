package com.etribunal.core.reactions;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReactionsController {

    private final ReactionService reactionService;
    private final CurrentUserResolver currentUser;

    public ReactionsController(ReactionService reactionService,
                               CurrentUserResolver currentUser) {
        this.reactionService = reactionService;
        this.currentUser = currentUser;
    }

    public record CreateReactionRequest(
            @NotBlank String target_type,
            @NotBlank String target_id,
            @NotBlank String emoji
        ) {
    }

    @PostMapping("/reactions")
    public ResponseEntity<ApiResponse<ReactionService.ReactionsSummary>> add(
            @Valid @RequestBody CreateReactionRequest dto,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(reactionService.addReaction(userId,
                parseTarget(dto.target_type()), parseId(dto.target_id()),
                parseEmoji(dto.emoji()))));
    }

    @DeleteMapping("/reactions")
    public ResponseEntity<ApiResponse<ReactionService.ReactionsSummary>> remove(
            @RequestParam String target_type,
            @RequestParam String target_id,
            @RequestParam String emoji,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(reactionService.removeReaction(userId,
                parseTarget(target_type), parseId(target_id), parseEmoji(emoji))));
    }

    @GetMapping("/reactions")
    public ResponseEntity<ApiResponse<ReactionService.ReactionsSummary>> summary(
            @RequestParam String target_type,
            @RequestParam String target_id,
            HttpServletRequest request) {
        var requester = currentUser.currentUserId(request).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(reactionService.getReactions(
                parseTarget(target_type), parseId(target_id), requester)));
    }

    private static ReactionTarget parseTarget(String value) {
        try {
            return ReactionTarget.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "target_type debe ser CASE o COMMENT");
        }
    }

    private static Emoji parseEmoji(String value) {
        try {
            return Emoji.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Emoji no permitido. Usa: LIKE, LOVE, ANGRY");
        }
    }

    private static UUID parseId(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "target_id inválido");
        }
    }
}
