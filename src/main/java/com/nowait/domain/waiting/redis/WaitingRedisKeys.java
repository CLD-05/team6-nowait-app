package com.nowait.domain.waiting.redis;

/*
 * 웨이팅 도메인 Redis 키 네이밍 컨벤션.
 *
 * 키 종류:
 *   waiting:session:{sid}:next-number   채번 카운터 (INCR)
 *   waiting:session:{sid}:count         현재 대기 팀 수 (INCR/DECR)
 *   waiting:session:{sid}:queue         실시간 순번 (Sorted Set)
 *   waiting:token:{token}               웨이팅 1건 상세 (Hash)
 *   waiting:user:{uid}:active           사용자 활성 토큰 (중복 등록 방지)
 *   waiting:pending-sync                Worker 동기화 큐 (List)
 */
public final class WaitingRedisKeys {

  private WaitingRedisKeys() {
  }

  /* 세션 단위 키 */
  public static String nextNumber(Long sessionId) {
    return "waiting:session:" + sessionId + ":next-number";
  }

  public static String count(Long sessionId) {
    return "waiting:session:" + sessionId + ":count";
  }

  public static String queue(Long sessionId) {
    return "waiting:session:" + sessionId + ":queue";
  }

  /* 토큰 단위 키 */
  public static String token(String token) {
    return "waiting:token:" + token;
  }

  /* 사용자 단위 키 (중복 등록 방지) */
  public static String userActive(Long userId) {
    return "waiting:user:" + userId + ":active";
  }

  /* Worker 동기화 큐 (전역) */
  public static final String PENDING_SYNC = "waiting:pending-sync";

  /*
   * Worker 가 RPOPLPUSH 로 pending-sync → processing 으로 옮긴 후 처리.
   * 처리 완료 시 LREM 으로 제거. 처리 중 죽으면 다음 부팅 시 여기서 복구 가능.
   */
  public static final String PROCESSING = "waiting:processing";

  /* 처리에 N 회 이상 실패한 메시지 격리 (운영자 수동 점검 필요) */
  public static final String DEAD_LETTER = "waiting:dead-letter";

  /* 활성 세션 ID 목록 (Worker 의 CALLED 타임아웃 스캔용) */
  public static final String ACTIVE_SESSIONS = "waiting:active-sessions";
}
