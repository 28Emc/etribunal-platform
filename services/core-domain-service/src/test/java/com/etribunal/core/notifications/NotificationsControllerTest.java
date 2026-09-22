package com.etribunal.core.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.common.domain.notification.NotificationType;
import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class NotificationsControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentUserResolver currentUser;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private NotificationsController controller;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        lenient().when(currentUser.requiredUserId(request)).thenReturn(userId);
    }

    @Test
    void getNotificationsClampsTakeToOneToFifty() {
        ResponseEntity<ApiResponse<NotificationService.NotificationsPage>> response =
                controller.getNotifications(request, 0, 500);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).getNotifications(userId, 0, 50);
    }

    @Test
    void markAsReadDelegatesToService() {
        UUID notificationId = UUID.randomUUID();
        when(notificationService.markAsRead(notificationId, userId))
                .thenReturn(new NotificationService.MarkAsReadResponse(true));

        ResponseEntity<ApiResponse<NotificationService.MarkAsReadResponse>> response =
                controller.markAsRead(notificationId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().success()).isTrue();
        verify(notificationService).markAsRead(notificationId, userId);
    }

    @Test
    void markAllAsReadDelegatesToService() {
        when(notificationService.markAllAsRead(userId))
                .thenReturn(new NotificationService.MarkAllAsReadResponse(true, 2));

        ResponseEntity<ApiResponse<NotificationService.MarkAllAsReadResponse>> response =
                controller.markAllAsRead(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().count()).isEqualTo(2);
        verify(notificationService).markAllAsRead(userId);
    }

    @Test
    void getUnreadCountDelegatesToService() {
        when(notificationService.getUnreadCount(userId))
                .thenReturn(new NotificationService.UnreadCountResponse(4L));

        ResponseEntity<ApiResponse<NotificationService.UnreadCountResponse>> response =
                controller.getUnreadCount(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().unread_count()).isEqualTo(4L);
        verify(notificationService).getUnreadCount(userId);
    }

    @Test
    void createInternalDelegatesToService() {
        UUID recipientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("caseId", UUID.randomUUID().toString());

        ResponseEntity<ApiResponse<Void>> response = controller.createInternal(
                new NotificationsController.InternalNotificationRequest(
                        recipientId, actorId, NotificationType.NEW_COMMENT, payload));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).createNotification(
                recipientId, actorId, NotificationType.NEW_COMMENT, payload);
    }
}