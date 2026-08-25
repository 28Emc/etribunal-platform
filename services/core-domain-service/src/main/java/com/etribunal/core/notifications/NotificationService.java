package com.etribunal.core.notifications;

import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final InternalUsersClient usersClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationService(NotificationRepository notificationRepository,
                               InternalUsersClient usersClient) {
        this.notificationRepository = notificationRepository;
        this.usersClient = usersClient;
    }

    @Transactional(readOnly = true)
    public NotificationsPage getNotifications(UUID userId, int skip, int take) {
        Pageable pageable = PageRequest.of(skip / take, take);
        List<NotificationEntity> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        // Extract actor_ids from payloads
        List<UUID> actorIds = notifications.stream()
                .map(this::extractActorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, UserSummary> actorMap = fetchActorMap(actorIds);

        List<NotificationResponse> responses = notifications.stream()
                .map(n -> toResponse(n, actorMap))
                .toList();

        long total = notificationRepository.countByUserId(userId);
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);

        return new NotificationsPage(responses, total, unreadCount);
    }

    @Transactional
    public MarkAsReadResponse markAsRead(UUID notificationId, UUID userId) {
        int updated = notificationRepository.markAsRead(notificationId, userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Notificación no encontrada");
        }
        return new MarkAsReadResponse(true);
    }

    @Transactional
    public MarkAllAsReadResponse markAllAsRead(UUID userId) {
        int count = notificationRepository.markAllAsRead(userId);
        return new MarkAllAsReadResponse(true, count);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUserIdAndReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    /**
     * Internal method for other services to create notifications.
     * Skips if recipient == actor or recipient has notifications disabled.
     */
    @Transactional
    public void createNotification(UUID recipientId, UUID actorId,
                                   NotificationType type, Map<String, Object> payload) {
        if (recipientId.equals(actorId)) {
            return;
        }

        NotificationEntity n = new NotificationEntity();
        n.setId(UUID.randomUUID());
        n.setUserId(recipientId);
        n.setType(type);
        n.setPayload(payload);
        n.setRead(false);
        n.setCreatedAt(Instant.now());
        notificationRepository.save(n);
    }

    // --- Helpers ---

    private UUID extractActorId(NotificationEntity n) {
        Map<String, Object> payload = n.getPayload();
        if (payload != null && payload.containsKey("actor_id")) {
            Object v = payload.get("actor_id");
            if (v != null) {
                return UUID.fromString(v.toString());
            }
        }
        return null;
    }

    private Map<UUID, UserSummary> fetchActorMap(List<UUID> actorIds) {
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return usersClient.summaries(actorIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    private NotificationResponse toResponse(NotificationEntity n,
                                            Map<UUID, UserSummary> actorMap) {
        UUID actorId = extractActorId(n);
        UserSummary actor = actorId != null ? actorMap.get(actorId) : null;

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(n.getPayload());
        } catch (Exception e) {
            payloadJson = "{}";
        }

        return new NotificationResponse(
                n.getId().toString(),
                n.getType().name(),
                payloadJson,
                n.isRead(),
                n.getCreatedAt(),
                actorId,
                actor != null ? actor.username() : null,
                actor != null ? actor.avatarUrl() : null
        );
    }

    // --- Response Records (snake_case) ---

    public record NotificationsPage(List<NotificationResponse> notifications,
                                    long total,
                                    long unread_count) {
    }

    public record NotificationResponse(String id, String type, String payload,
                                       boolean is_read, Instant created_at,
                                       UUID actor_id, String actor_username,
                                       String actor_avatar) {
    }

    public record MarkAsReadResponse(boolean success) {
    }

    public record MarkAllAsReadResponse(boolean success, int count) {
    }

    public record UnreadCountResponse(long unread_count) {
    }
}