package com.etribunal.core.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.notification.NotificationType;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private InternalUsersClient usersClient;

    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-25T12:00:00Z");

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, usersClient);
    }

    @Test
    void getNotificationsReturnsPaginatedWithActorEnrichment() {
        NotificationEntity n1 = notification(UUID.randomUUID(), actorId, "NEW_COMMENT", now);
        NotificationEntity n2 = notification(UUID.randomUUID(), actorId, "NEW_VOTE", now.minusSeconds(60));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(List.of(n1, n2));
        when(notificationRepository.countByUserId(userId)).thenReturn(2L);
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(1L);
        when(usersClient.summaries(List.of(actorId)))
                .thenReturn(List.of(new UserSummary(actorId, "actor_user", "https://avatar.png", false)));

        var result = notificationService.getNotifications(userId, 0, 20);

        assertThat(result.notifications()).hasSize(2);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.unread_count()).isEqualTo(1);
        assertThat(result.notifications().get(0).actor_username()).isEqualTo("actor_user");
    }

    @Test
    void getNotificationsEmptyWhenNoNotifications() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(notificationRepository.countByUserId(userId)).thenReturn(0L);
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(0L);

        var result = notificationService.getNotifications(userId, 0, 20);

        assertThat(result.notifications()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
        assertThat(result.unread_count()).isEqualTo(0);
    }

    @Test
    void markAsReadUpdatesNotification() {
        when(notificationRepository.markAsRead(any(), any())).thenReturn(1);

        var response = notificationService.markAsRead(UUID.randomUUID(), userId);

        assertThat(response.success()).isTrue();
        verify(notificationRepository).markAsRead(any(), eq(userId));
    }

    @Test
    void markAsReadThrowsWhenNotFound() {
        when(notificationRepository.markAsRead(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> notificationService.markAsRead(UUID.randomUUID(), userId))
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void markAllAsReadReturnsCount() {
        when(notificationRepository.markAllAsRead(userId)).thenReturn(5);

        var response = notificationService.markAllAsRead(userId);

        assertThat(response.success()).isTrue();
        assertThat(response.count()).isEqualTo(5);
    }

    @Test
    void getUnreadCountReturnsCount() {
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(3L);

        var response = notificationService.getUnreadCount(userId);

        assertThat(response.unread_count()).isEqualTo(3);
    }

    @Test
    void createNotificationSkipsWhenRecipientEqualsActor() {
        notificationService.createNotification(userId, userId,
                NotificationType.NEW_COMMENT, Map.of("case_id", "abc"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotificationSavesWithPayload() {
        notificationService.createNotification(UUID.randomUUID(), actorId,
                NotificationType.NEW_COMMENT, Map.of("case_id", "abc", "actor_id", actorId.toString()));

        ArgumentCaptor<NotificationEntity> captor =
                ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.NEW_COMMENT);
        assertThat(captor.getValue().getPayload()).containsKey("case_id");
    }

    private NotificationEntity notification(UUID id, UUID actorId, String type, Instant createdAt) {
        NotificationEntity n = new NotificationEntity();
        n.setId(id);
        n.setUserId(userId);
        n.setType(NotificationType.valueOf(type));
        n.setRead(false);
        n.setCreatedAt(createdAt);
        n.setPayload(Map.of("actor_id", actorId.toString(), "case_id", "test-case"));
        return n;
    }
}