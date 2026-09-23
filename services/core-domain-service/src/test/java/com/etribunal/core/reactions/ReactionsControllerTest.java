package com.etribunal.core.reactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReactionsControllerTest {

    @Mock
    private ReactionService reactionService;
    @Mock
    private CurrentUserResolver currentUser;
    @Mock
    private HttpServletRequest request;

    private ReactionsController controller;

    private UUID userId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        controller = new ReactionsController(reactionService, currentUser);
        userId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    @Test
    void add_postValidRequestDelegatesToService() {
        when(currentUser.requiredUserId(request)).thenReturn(userId);
        ReactionService.ReactionsSummary summary =
                new ReactionService.ReactionsSummary(List.of(), null);
        when(reactionService.addReaction(userId, ReactionTarget.CASE, targetId, Emoji.LIKE))
                .thenReturn(summary);

        ResponseEntity<ApiResponse<ReactionService.ReactionsSummary>> response =
                controller.add(new ReactionsController.CreateReactionRequest(
                        "case", targetId.toString(), "LIKE"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().data()).isSameAs(summary);
        verify(reactionService).addReaction(userId, ReactionTarget.CASE, targetId, Emoji.LIKE);
    }

    @Test
    @SuppressWarnings("java:S5778")
    void add_invalidTargetThrowsBadRequest() {
        when(currentUser.requiredUserId(request)).thenReturn(userId);

        assertThatThrownBy(() -> addRequest("invalid", targetId.toString(), "LIKE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CASE o COMMENT");
    }

    @Test
    @SuppressWarnings("java:S5778")
    void add_invalidEmojiThrowsBadRequest() {
        when(currentUser.requiredUserId(request)).thenReturn(userId);

        assertThatThrownBy(() -> addRequest("case", targetId.toString(), "NOPE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LIKE, LOVE, ANGRY");
    }

    @Test
    @SuppressWarnings("java:S5778")
    void add_invalidIdThrowsBadRequest() {
        when(currentUser.requiredUserId(request)).thenReturn(userId);

        assertThatThrownBy(() -> addRequest("case", "not-a-uuid", "LIKE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("target_id");
    }

    private void addRequest(String targetType, String targetId, String emoji) {
        controller.add(new ReactionsController.CreateReactionRequest(targetType, targetId, emoji), request);
    }

    @Test
    void remove_delegatesToService() {
        when(currentUser.requiredUserId(request)).thenReturn(userId);
        ReactionService.ReactionsSummary summary =
                new ReactionService.ReactionsSummary(List.of(), "LIKE");
        when(reactionService.removeReaction(userId, ReactionTarget.COMMENT, targetId, Emoji.ANGRY))
                .thenReturn(summary);

        ResponseEntity<ApiResponse<ReactionService.ReactionsSummary>> response =
                controller.remove("COMMENT", targetId.toString(), "ANGRY", request);

        assertThat(response.getBody().data()).isSameAs(summary);
        verify(reactionService).removeReaction(userId, ReactionTarget.COMMENT, targetId, Emoji.ANGRY);
    }

    @Test
    void summary_delegatesToServiceWithoutRequesterWhenAnonymous() {
        when(currentUser.currentUserId(request)).thenReturn(Optional.empty());
        ReactionService.ReactionsSummary summary =
                new ReactionService.ReactionsSummary(List.of(), null);
        when(reactionService.getReactions(ReactionTarget.CASE, targetId, null))
                .thenReturn(summary);

        ResponseEntity<ApiResponse<ReactionService.ReactionsSummary>> response =
                controller.summary("case", targetId.toString(), request);

        assertThat(response.getBody().data()).isSameAs(summary);
        verify(reactionService).getReactions(ReactionTarget.CASE, targetId, null);
    }

    @Test
    void summary_delegatesWithRequesterWhenLoggedIn() {
        when(currentUser.currentUserId(request)).thenReturn(Optional.of(userId));
        when(reactionService.getReactions(ReactionTarget.COMMENT, targetId, userId))
                .thenReturn(new ReactionService.ReactionsSummary(List.of(), null));

        controller.summary("COMMENT", targetId.toString(), request);

        verify(reactionService).getReactions(ReactionTarget.COMMENT, targetId, userId);
    }
}