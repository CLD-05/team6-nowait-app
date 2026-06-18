package com.nowait.domain.waiting.redis;

/*
 * 웨이팅 도메인 Redis 키 네이밍 컨벤션.
 *
 * 키 종류:
 *   waiting:session:{sid}:next-number             채번 카운터 (INCR)
 *   waiting:session:{sid}:count                   현재 대기 팀 수 (INCR/DECR)
 *   waiting:session:{sid}:queue                   실시간 순번 (Sorted Set)
 *   waiting:token:{token}                         웨이팅 1건 상세 (Hash)
 *   waiting:user:{uid}:restaurant:{rid}:active    사용자 + 식당별 활성 토큰 (식당 단위 중복 등록 방지)
 *   waiting:pending-sync                          Worker 동기화 큐 (List)
 */
public final class WaitingRedisKeys {

  private WaitingRedisKeys() {
  }

  /*
   * 환경 prefix — REDIS_KEY_PREFIX 환경변수가 설정된 경우에만 키 앞에 붙는다.
   * ReservationRedisKeys와 동일한 방어 장치. 설정하지 않으면 기존과 동일하게 동작한다.
   */
  private static final String PREFIX = resolvePrefix();

  private static String resolvePrefix() {
    String raw = System.getenv("REDIS_KEY_PREFIX");
    if (raw == null || raw.isBlank()) {
      return "";
    }
    return raw.endsWith(":") ? raw : raw + ":";
  }

  /* 세션 단위 키 */
  public static String nextNumber(Long sessionId) {
    return PREFIX + "waiting:session:" + sessionId + ":next-number";
  }

  public static String count(Long sessionId) {
    return PREFIX + "waiting:session:" + sessionId + ":count";
  }

  public static String queue(Long sessionId) {
    return PREFIX + "waiting:session:" + sessionId + ":queue";
  }

  /* 토큰 단위 키 */
  public static String token(String token) {
    return PREFIX + "waiting:token:" + token;
  }

  /* 사용자 + 식당 단위 키 (식당 단위 중복 등록 방지 — 같은 사용자가 다른 식당엔 동시 등록 가능) */
  public static String userActive(Long userId, Long restaurantId) {
    return PREFIX + "waiting:user:" + userId + ":restaurant:" + restaurantId + ":active";
  }

  /* SCAN 용 패턴 — 특정 사용자의 모든 활성 식당 키 매칭 */
  public static String userActivePattern(Long userId) {
    return PREFIX + "waiting:user:" + userId + ":restaurant:*:active";
  }

  /* Worker 동기화 큐 (전역) */
  public static final String PENDING_SYNC = PREFIX + "waiting:pending-sync";

  /*
   * Worker 가 RPOPLPUSH 로 pending-sync → processing 으로 옮긴 후 처리.
   * 처리 완료 시 LREM 으로 제거. 처리 중 죽으면 다음 부팅 시 여기서 복구 가능.
   */
  public static final String PROCESSING = PREFIX + "waiting:processing";

  /* 처리에 N 회 이상 실패한 메시지 격리 (운영자 수동 점검 필요) */
  public static final String DEAD_LETTER = PREFIX + "waiting:dead-letter";

  /* 활성 세션 ID 목록 (Worker 의 CALLED 타임아웃 스캔용) */
  public static final String ACTIVE_SESSIONS = PREFIX + "waiting:active-sessions";
}
