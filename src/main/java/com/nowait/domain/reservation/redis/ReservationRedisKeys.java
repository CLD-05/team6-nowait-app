package com.nowait.domain.reservation.redis;

/*
 * 예약 도메인 Redis 키 네이밍 컨벤션.
 *
 * 키 종류:
 *   reservation:token:{token}                    Hash    예약 1건 상세
 *   reservation:slot:{slotId}:count              String  슬롯 점유 카운터 (INCR/DECR)
 *   reservation:slot:{slotId}:queue              ZSET    슬롯의 활성 토큰 (score=createdAt)
 *   reservation:user-slot:{uid}:{slotId}         String  동일 슬롯 중복 예약 방지
 *   reservation:user:{uid}:tokens                ZSET    사용자별 전체 토큰 목록 (score=createdAt)
 *   reservation:restaurant:{rid}:tokens          ZSET    매장별 전체 토큰 목록 (score=createdAt)
 *   reservation:noshow-candidates                ZSET    score=reservationTime, 스케줄러 만료 탐색
 *   reservation:pending-sync                     List    Worker 동기화 큐
 *   reservation:processing                       List    Worker in-flight
 *   reservation:dead-letter                      List    실패 격리
 */
public final class ReservationRedisKeys {

  private ReservationRedisKeys() {
  }

  /* 토큰 단위 Hash */
  public static String token(String token) {
    return "reservation:token:" + token;
  }

  /* 슬롯 단위 키 */
  public static String slotCount(Long slotId) {
    return "reservation:slot:" + slotId + ":count";
  }

  public static String slotQueue(Long slotId) {
    return "reservation:slot:" + slotId + ":queue";
  }

  /* 사용자-슬롯 페어 (동일 슬롯 중복 방지) */
  public static String userSlot(Long userId, Long slotId) {
    return "reservation:user-slot:" + userId + ":" + slotId;
  }

  /* 사용자별 전체 토큰 목록 ZSET (score = createdAt millis) */
  public static String userTokens(Long userId) {
    return "reservation:user:" + userId + ":tokens";
  }

  /* 매장별 전체 토큰 목록 ZSET (score = createdAt millis) */
  public static String restaurantTokens(Long restaurantId) {
    return "reservation:restaurant:" + restaurantId + ":tokens";
  }

  /* 노쇼 스케줄러 ZSET (score = 예약 시각 millis) */
  public static final String NOSHOW_CANDIDATES = "reservation:noshow-candidates";

  /* Worker 큐 */
  public static final String PENDING_SYNC = "reservation:pending-sync";
  public static final String PROCESSING = "reservation:processing";
  public static final String DEAD_LETTER = "reservation:dead-letter";
}
