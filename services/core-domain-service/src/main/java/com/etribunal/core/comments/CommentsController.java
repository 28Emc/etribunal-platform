package com.etribunal.core.comments;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentsController {

    private final CommentService commentService;
    private final CurrentUserResolver currentUser;

    public CommentsController(CommentService commentService,
                              CurrentUserResolver currentUser) {
        this.commentService = commentService;
        this.currentUser = currentUser;
    }

    public record CreateCommentRequest(
            @NotBlank @Size(max = 2000) String content,
            @Size(max = 36) String parent_id,
            Boolean is_anonymous
        ) {
    }

    public record UpdateCommentRequest(
            @NotBlank @Size(max = 2000) String content
        ) {
    }

    @GetMapping("/cases/{caseId}/comments")
    public ResponseEntity<ApiResponse<CommentService.CommentPage>> list(
            @PathVariable UUID caseId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                commentService.getCommentsCursor(caseId, before, after, limit)));
    }

    @GetMapping("/cases/{caseId}/comments/new")
    public ResponseEntity<ApiResponse<Long>> newCount(
            @PathVariable UUID caseId,
            @RequestParam(required = false) String since) {
        return ResponseEntity.ok(ApiResponse.ok(
                commentService.getNewCommentsCount(caseId, since)));
    }

    @PostMapping("/cases/{caseId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateCommentRequest dto,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        UUID parentId = dto.parent_id() != null && !dto.parent_id().isBlank()
                ? UUID.fromString(dto.parent_id()) : null;
        return ResponseEntity.status(201).body(ApiResponse.ok(
                commentService.createComment(caseId, userId, dto.content(),
                        parentId, Boolean.TRUE.equals(dto.is_anonymous()))));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest dto,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                commentService.updateComment(commentId,
                        currentUser.requiredUserId(request), dto.content())));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID commentId,
            HttpServletRequest request) {
        commentService.deleteComment(commentId,
                currentUser.requiredUserId(request));
        return ResponseEntity.ok(ApiResponse.ok(null, "Comentario eliminado"));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> replies(
            @PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                commentService.getReplies(commentId)));
    }
}
