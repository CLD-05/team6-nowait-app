package com.nowait.domain.reservation.repository;

import com.nowait.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 내 예약 목록 조회 (최신순) — LEFT JOIN FETCH: restaurant soft-delete 필터 우회
    @Query("SELECT r FROM Reservation r JOIN FETCH r.user LEFT JOIN FETCH r.restaurant JOIN FETCH r.slot WHERE r.user.id = :userId ORDER BY r.createdAt DESC")
    List<Reservation> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    // Worker / 조회: token 으로 기존 행 조회 (idempotent upsert 의 키)
    Optional<Reservation> findByReservationToken(String reservationToken);

    // Worker(멱등성): UNIQUE 충돌/복구 시 해당 token 이 이미 DB 에 저장됐는지 확인
    boolean existsByReservationToken(String reservationToken);

    // 취소/노쇼 내역 삭제용 — 본인 소유 확인
    @Query("SELECT r FROM Reservation r WHERE r.reservationToken = :token AND r.user.id = :userId")
    Optional<Reservation> findByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);

    // 점주용 — 식당의 예약 목록 (최신순) — LEFT JOIN FETCH: soft-delete 필터 우회
    @Query("SELECT r FROM Reservation r JOIN FETCH r.user LEFT JOIN FETCH r.restaurant JOIN FETCH r.slot WHERE r.restaurant.id = :restaurantId ORDER BY r.createdAt DESC")
    List<Reservation> findByRestaurantIdOrderByCreatedAtDesc(@Param("restaurantId") Long restaurantId);
}
