package com.nowait.domain.waiting.repository;

import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.type.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitingRepository extends JpaRepository<Waiting, Long> {

  /* 점주: 세션의 모든 웨이팅 목록 (번호 오름차순) */
  List<Waiting> findBySessionIdOrderByWaitingNumberAsc(Long sessionId);

  /* 세션의 활성 웨이팅 목록 (번호 오름차순) — 앞 N팀 알림 판별용 */
  List<Waiting> findBySessionIdAndStatusInOrderByWaitingNumberAsc(
      Long sessionId, Collection<WaitingStatus> statuses);

  /* 사용자: 내 활성 웨이팅 (WAITING/CALLED) — 가장 최근 1건 */
  Optional<Waiting> findFirstByUserIdAndStatusInOrderByRegisteredAtDesc(
      Long userId, Collection<WaitingStatus> statuses);

  /* 중복 등록 방지: 같은 세션에 활성 웨이팅이 이미 있는지 */
  boolean existsByUserIdAndSessionIdAndStatusIn(
      Long userId, Long sessionId, Collection<WaitingStatus> statuses);

  /* 앞에 남은 대기 팀 수 (내 번호보다 작고 WAITING/CALLED 상태) */
  @Query("""
      SELECT COUNT(w) FROM Waiting w
      WHERE w.sessionId = :sessionId
        AND w.status IN :statuses
        AND w.waitingNumber < :waitingNumber
      """)
  long countAhead(@Param("sessionId") Long sessionId,
      @Param("statuses") Collection<WaitingStatus> statuses,
      @Param("waitingNumber") int waitingNumber);

  /*
   * 다음 대기번호 채번: 세션의 현재 max + 1
   * ⚠️ 트랜잭션 외부 race condition 가능 — Redis 통합 시 해결
   * UNIQUE 제약 (session_id, waiting_number) 이 최종 방어선
   */
  @Query("SELECT COALESCE(MAX(w.waitingNumber), 0) FROM Waiting w WHERE w.sessionId = :sessionId")
  int findMaxWaitingNumber(@Param("sessionId") Long sessionId);

  /* 스케줄러: 호출 후 타임아웃된 CALLED 상태 웨이팅 조회 */
  List<Waiting> findByStatusAndCalledAtBefore(WaitingStatus status, LocalDateTime threshold);

  /* Worker: token 으로 기존 행 조회 (idempotent upsert 의 키) */
  Optional<Waiting> findByWaitingToken(String waitingToken);

  /* 마이페이지: 사용자의 전체 웨이팅 이력 (최신순) */
  List<Waiting> findByUserIdOrderByRegisteredAtDesc(Long userId);
}