package com.nowait.domain.waiting.scheduler;

/*
 * [DEPRECATED — Phase 3]
 *
 * 본 스케줄러는 더 이상 사용되지 않는다.
 * CALLED 타임아웃 자동 취소는 Worker Pod 의
 *   {@code com.nowait.domain.waiting.worker.WaitingCallTimeoutScheduler}
 * 가 Redis active-sessions 를 스캔하는 방식으로 대체되었다.
 *
 * 본 파일은 git 히스토리 보존용 placeholder 로 남겨둔다.
 * 다음 PR 에서 제거 예정.
 */
@Deprecated(forRemoval = true)
public final class WaitingTimeoutScheduler {
  private WaitingTimeoutScheduler() {}
}
