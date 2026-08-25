package com.etribunal.core.notifications;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationsController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUser;

    public NotificationsController(NotificationService notificationService,
                                   CurrentUserResolver currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    // GET /notifications
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationService.NotificationsPage>> getNotifications(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int take) {
        UUID userId = currentUser.requiredUserId(request);
        int safeTake = Math.min(Math.max(take, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getNotifications(userId, skip, safeTake)));
    }

    // PATCH /notifications/{id}/read
    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<NotificationService.MarkAsReadResponse>> markAsRead(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.markAsRead(id, userId)));
    }

    // PATCH /notifications/read-all
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<NotificationService.MarkAllAsReadResponse>> markAllAsRead(
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.markAllAsRead(userId)));
    }

    // GET /notifications/unread-count
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<NotificationService.UnreadCountResponse>> getUnreadCount(
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getUnreadCount(userId)));
    }
}