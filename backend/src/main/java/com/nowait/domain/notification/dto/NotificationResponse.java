package com.nowait.domain.notification.dto;

import com.nowait.domain.notification.entity.Notification;
import com.nowait.domain.notification.type.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(

    Long notificationId,
    NotificationType type,
    String message,
    String isRead,
    LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getMessage(),
            notification.getIsRead(),
            notification.getCreatedAt()
        );
    }
}
