package com.nowait.domain.notification.controller;

import com.nowait.domain.notification.dto.NotificationResponse;
import com.nowait.domain.notification.service.NotificationService;
import com.nowait.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications/me
     * 내 알림 목록 (인증 필요)
     * - ?unread=true 면 안 읽은 알림만
     */
    @GetMapping("/api/notifications/me")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(name = "unread", defaultValue = "false") boolean unreadOnly
    ) {
        return ResponseEntity.ok(
            notificationService.getMyNotifications(userDetails.getUserId(), unreadOnly)
        );
    }

    /**
     * PATCH /api/notifications/{notificationId}/read
     * 알림 읽음 처리 (본인만)
     */
    @PatchMapping("/api/notifications/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(userDetails.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/notifications/read-all
     * 전체 읽음 처리 (본인 알림 일괄)
     */
    @PatchMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllAsRead(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationService.markAllAsRead(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/notifications/{notificationId}
     * 알림 삭제 (본인만)
     */
    @DeleteMapping("/api/notifications/{notificationId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long notificationId
    ) {
        notificationService.delete(userDetails.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
