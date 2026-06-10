package com.nowait.domain.reservation.entity;

import com.nowait.domain.reservation.type.ReservationStatus;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.user.entity.User;
import com.nowait.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
 * 예약 엔티티 — Redis-first 아키텍처.
 *
 * - source of truth 는 Redis Hash (reservation:token:{token})
 * - DB 는 Worker (Phase 6) 가 비동기 sync 한 결과를 보관
 * - 따라서 본 엔티티는 자체 상태 전이 메서드를 두지 않음
 *   (Phase 6 에서 register() / syncFromRedis() 추가 예정)
 */
@Entity
@Table(
    name = "reservation",
    indexes = {
        @Index(name = "idx_reservation_user_id", columnList = "user_id"),
        @Index(name = "idx_reservation_restaurant_id", columnList = "restaurant_id"),
        @Index(name = "idx_reservation_slot_id", columnList = "slot_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* Redis 와 DB 를 잇는 자연키 — Worker 의 idempotent upsert 키 */
    @Column(name = "reservation_token", nullable = false, length = 36, unique = true)
    private String reservationToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @Column(nullable = false)
    private int headcount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "no_show_at")
    private LocalDateTime noShowAt;

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
