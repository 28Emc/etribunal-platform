package com.etribunal.identity.notifications;

import com.etribunal.common.domain.config.InternalApiProperties;
import com.etribunal.common.domain.notification.NotificationType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InternalNotificationsClient {

    private final RestClient restClient;

    public InternalNotificationsClient(RestClient.Builder builder,
                                       InternalApiProperties properties) {
        this.restClient = builder
                .baseUrl(properties.coreBaseUrl())
                .defaultHeader("X-Internal-Token", properties.token())
                .build();
    }

    public void createNotification(UUID recipientId, UUID actorId,
                                   NotificationType type, Map<String, Object> payload) {
        restClient.post()
                .uri("/api/notifications/internal/create")
                .body(new InternalCreateRequest(recipientId, actorId, type, payload))
                .retrieve()
                .toBodilessEntity();
    }

    private record InternalCreateRequest(
            UUID recipientId,
            UUID actorId,
            NotificationType type,
            Map<String, Object> payload) {
    }
}