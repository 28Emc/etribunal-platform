package com.etribunal.ai.automation.infrastructure.api;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CoreApiClient {

    private static final Logger log = LoggerFactory.getLogger(CoreApiClient.class);

    private final WebClient webClient;
    private final Duration timeout;

    public CoreApiClient(WebClient.Builder webClientBuilder, AutomationConfig config) {
        this.timeout = Duration.ofSeconds(config.getBotAuth().getHttpTimeoutSeconds());
        this.webClient = webClientBuilder
                .baseUrl(config.getBotAuth().getCoreUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public UUID vote(String token, UUID caseId, String option) {
        Map<String, String> body = Map.of("vote_type", option);
        ApiResponse<VoteResponse> response = webClient.post()
                .uri("/cases/{caseId}/votes", caseId)
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ApiResponse<VoteResponse>>() {})
                .block(timeout);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("vote: empty response from core API for case " + caseId);
        }
        return UUID.fromString(response.data().case_id());
    }

    public void deleteVote(String token, UUID caseId) {
        webClient.delete()
                .uri("/cases/{caseId}/votes", caseId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block(timeout);
    }

    public UUID createComment(String token, UUID caseId, String content, UUID parentId, boolean isAnonymous) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("parent_id", parentId != null ? parentId.toString() : null);
        body.put("is_anonymous", isAnonymous);
        ApiResponse<CommentResponse> response = webClient.post()
                .uri("/cases/{caseId}/comments", caseId)
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ApiResponse<CommentResponse>>() {})
                .block(timeout);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("createComment: empty response from core API for case " + caseId);
        }
        return response.data().id();
    }

    public void deleteComment(String token, UUID commentId) {
        webClient.delete()
                .uri("/comments/{commentId}", commentId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block(timeout);
    }

    public UUID addReaction(String token, String targetType, UUID targetId, String emoji) {
        Map<String, String> body = Map.of(
                "target_type", targetType,
                "target_id", targetId.toString(),
                "emoji", emoji
        );
        // La API de reacciones devuelve ReactionsSummary, no un ID único.
        // Para el tracking del motor, usamos un ID sintético basado en target+user+emoji
        // ya que la reacción es un toggle (upsert).
        webClient.post()
                .uri("/reactions")
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ApiResponse<ReactionSummary>>() {})
                .block(timeout);
        // Retornamos un UUID sintético determinista para tracking
        return UUID.nameUUIDFromBytes((targetType + ":" + targetId + ":" + emoji).getBytes());
    }

    public void removeReaction(String token, String targetType, UUID targetId, String emoji) {
        webClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/reactions")
                        .queryParam("target_type", targetType)
                        .queryParam("target_id", targetId.toString())
                        .queryParam("emoji", emoji)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block(timeout);
    }

    // Response DTOs matching core APIs (wrapped in ApiResponse)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiResponse<T>(T data, String message, int statusCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VoteResponse(
            String case_id,
            String vote_type,
            int votes_a,
            int votes_b,
            int votes_both_wrong
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentResponse(UUID id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReactionSummary(
            java.util.List<EmojiCount> reactions,
            String user_reaction
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmojiCount(String emoji, long count) {}
}