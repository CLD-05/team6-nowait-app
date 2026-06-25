package com.nowait.domain.reservation.redis;

import java.time.LocalDate;

/*
 * 예약 도메인 Redis 키 네이밍 컨벤션.
 *
 * 키 종류:
 *   reservation:token:{token}                    Hash    예약 1건 상세
 *   reservation:slot:{slotId}:count              String  슬롯 점유 카운터 (INCR/DECR)
 *   reservation:slot:{slotId}:queue              ZSET    슬롯의 활성 토큰 (score=createdAt)
 *   reservation:user-slot:{uid}:{slotId}         String  동일 슬롯 중복 예약 방지
 *   reservation:user-restaurant-date:{uid}:{rid}:{date}  String  같은 매장·같은 날짜 중복 예약 방지
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

  /*
   * 환경 prefix — REDIS_KEY_PREFIX 환경변수가 설정된 경우에만 키 앞에 붙는다.
   * dev/prod는 서로 다른 ElastiCache 인스턴스를 쓰지만(별도 Terraform root module),
   * 로컬 테스트나 k6 부하테스트가 같은 Redis를 공유하는 경우의 키 충돌을 막기 위한
   * 방어 장치. 설정하지 않으면 기존과 동일하게 동작한다.
   */
  private static final String PREFIX = resolvePrefix();

  private static String resolvePrefix() {
    String raw = System.getenv("REDIS_KEY_PREFIX");
    if (raw == null || raw.isBlank()) {
      return "";
    }
    return raw.endsWith(":") ? raw : raw + ":";
  }

  /* 토큰 단위 Hash */
  public static String token(String token) {
    return PREFIX + "reservation:token:" + token;
  }

  /* 슬롯 단위 키 */
  public static String slotCount(Long slotId) {
    return PREFIX + "reservation:slot:" + slotId + ":count";
  }

  public static String slotQueue(Long slotId) {
    return PREFIX + "reservation:slot:" + slotId + ":queue";
  }

  /* 사용자-슬롯 페어 (동일 슬롯 중복 방지) */
  public static String userSlot(Long userId, Long slotId) {
    return PREFIX + "reservation:user-slot:" + userId + ":" + slotId;
  }

  /* 사용자-매장-날짜 (같은 매장·같은 날짜 중복 예약 방지 — 시간대가 달라도 차단) */
  public static String userRestaurantDate(Long userId, Long restaurantId, LocalDate date) {
    return PREFIX + "reservation:user-restaurant-date:" + userId + ":" + restaurantId + ":" + date;
  }

  /* 사용자별 전체 토큰 목록 ZSET (score = createdAt millis) */
  public static String userTokens(Long userId) {
    return PREFIX + "reservation:user:" + userId + ":tokens";
  }

  /* 매장별 전체 토큰 목록 ZSET (score = createdAt millis) */
  public static String restaurantTokens(Long restaurantId) {
    return PREFIX + "reservation:restaurant:" + restaurantId + ":tokens";
  }

  /* 노쇼 스케줄러 ZSET (score = 예약 시각 millis) */
  public static final String NOSHOW_CANDIDATES = PREFIX + "reservation:noshow-candidates";

  /* Worker 큐 */
  public static final String PENDING_SYNC = PREFIX + "reservation:pending-sync";
  public static final String PROCESSING = PREFIX + "reservation:processing";
  public static final String DEAD_LETTER = PREFIX + "reservation:dead-letter";
}
