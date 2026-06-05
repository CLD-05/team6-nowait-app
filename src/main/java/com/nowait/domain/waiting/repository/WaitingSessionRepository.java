package com.nowait.domain.waiting.repository;

import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.type.WaitingSessionStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitingSessionRepository extends JpaRepository<WaitingSession, Long> {

  /*
   * 특정 식당의 특정 날짜 세션 조회
   * - 사용자: 오늘 세션 현황 조회 시 사용
   * - 점주: 오늘 이미 세션 오픈했는지 확인 시 사용
   * - DDL UNIQUE 제약: (restaurant_id, session_date) → 최대 1건
   */
  Optional<WaitingSession> findByRestaurantIdAndSessionDate(Long restaurantId, LocalDate sessionDate);

  /*
   * 특정 식당의 특정 날짜 세션 존재 여부
   * - 세션 오픈 시 중복 방지용
   * - DDL UNIQUE 제약이 최종 방어선이지만, 사전 체크로 명확한 에러 메시지 제공
   */
  boolean existsByRestaurantIdAndSessionDate(Long restaurantId, LocalDate sessionDate);

  // 스케줄러: 오늘 자정 지난 미마감 세션 자동 마감 (필요 시)
  List<WaitingSession> findByStatusInAndSessionDateBefore(
      Collection<WaitingSessionStatus> statuses, LocalDate date);
}
