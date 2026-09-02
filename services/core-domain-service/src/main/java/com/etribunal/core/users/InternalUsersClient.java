package com.etribunal.core.users;

import com.etribunal.common.domain.config.InternalApiProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP interno contra identity-service para resolver datos de usuario
 * (users vive en otra base de datos: cross-service lookup).
 */
@Component
public class InternalUsersClient {

    private static final ParameterizedTypeReference<List<UserSummary>> SUMMARIES_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final InternalApiProperties properties;

    public InternalUsersClient(RestClient.Builder builder,
                               InternalApiProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.identityBaseUrl())
                .defaultHeader("X-Internal-Token", properties.token())
                .build();
    }

    public List<UserSummary> summaries(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        String ids = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<UserSummary> result = restClient.get()
                .uri("/users/internal/summaries?ids={ids}", ids)
                .retrieve()
                .body(SUMMARIES_TYPE);
        return result != null ? result : Collections.emptyList();
    }

    public List<UUID> followingIds(UUID userId) {
        UUID[] result = restClient.get()
                .uri("/users/internal/following-ids")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(UUID[].class);
        return result != null ? Arrays.asList(result) : Collections.emptyList();
    }

    public List<Map<String, Object>> searchUsers(String query, int take, int skip,
                                                 UUID requesterId) {
        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }
        var request = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users/internal/search")
                        .queryParam("q", query.trim())
                        .queryParam("take", take)
                        .queryParam("skip", skip)
                        .build());
        if (requesterId != null) {
            request = request.header("X-User-Id", requesterId.toString());
        }
        List<Map<String, Object>> result = request.retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
        return result != null ? result : Collections.emptyList();
    }

    public UserSummary findByUsername(String username) {
        List<Map<String, Object>> results = searchUsers(username, 1, 0, null);
        if (results.isEmpty()) {
            return null;
        }
        Map<String, Object> user = results.get(0);
        return new UserSummary(
                UUID.fromString((String) user.get("id")),
                (String) user.get("username"),
                (String) user.get("avatar_url"),
                Boolean.TRUE.equals(user.get("is_anonymous")));
    }
}
