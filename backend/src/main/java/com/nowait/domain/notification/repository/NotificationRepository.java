package com.nowait.domain.notification.repository;

import com.nowait.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림 목록 (최신순)
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 내 안 읽은 알림 목록 (최신순)
    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, String isRead);

    // 전체 읽음 처리 (벌크 update)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = 'Y' WHERE n.user.id = :userId AND n.isRead = 'N'")
    int markAllAsRead(@Param("userId") Long userId);
}
