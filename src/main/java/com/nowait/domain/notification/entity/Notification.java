package com.nowait.domain.notification.entity;

import com.nowait.domain.notification.type.NotificationType;
import com.nowait.domain.user.entity.User;
import com.nowait.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "notification",
    indexes = {
        @Index(
            name = "idx_notif_user_unread",
            columnList = "user_id, is_read, created_at"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false, length = 1)
    private String isRead;

    @Builder
    public Notification(User user, NotificationType type, String message) {
        this.user = user;
        this.type = type;
        this.message = message;
        this.isRead = "N";
    }

    public void markAsRead() {
        this.isRead = "Y";
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
