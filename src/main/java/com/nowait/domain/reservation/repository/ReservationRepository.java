package com.nowait.domain.reservation.repository;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.type.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 내 예약 목록 조회 (최신순)
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 슬롯 중복 예약 체크 (CONFIRMED 상태인 것만)
    boolean existsByUserIdAndSlotIdAndStatus(Long userId, Long slotId, ReservationStatus status);

    // 예약 상세 조회 (연관 엔티티 함께)
    Optional<Reservation> findWithDetailsById(Long id);
}