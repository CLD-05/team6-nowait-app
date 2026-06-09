package com.nowait.domain.notification.service;

import com.nowait.domain.notification.dto.NotificationResponse;
import com.nowait.domain.notification.entity.Notification;
import com.nowait.domain.notification.repository.NotificationRepository;
import com.nowait.domain.notification.type.NotificationType;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * [내부 호출용] 알림 생성
     * - 다른 도메인 서비스가 이벤트 발생 시 호출
     * - 부모 트랜잭션에 합류(REQUIRED) → 부모 롤백 시 알림도 롤백
     */
    @Transactional
    public void notify(Long userId, NotificationType type, String message) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
            .user(user)
            .type(type)
            .message(message)
            .build();

        notificationRepository.save(notification);
    }

    /**
     * 내 알림 목록 조회
     * - unreadOnly = true → 안 읽은 것만
     */
    public List<NotificationResponse> getMyNotifications(Long userId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
            ? notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, "N")
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return notifications.stream()
            .map(NotificationResponse::from)
            .toList();
    }

    /**
     * 알림 읽음 처리 (본인만)
     */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markAsRead();
    }

    /**
     * 전체 읽음 처리 (벌크 update)
     * @return 영향 받은 row 수
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    /**
     * 알림 삭제 (본인만)
     */
    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notificationRepository.delete(notification);
    }
}
