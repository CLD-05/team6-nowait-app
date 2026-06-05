package com.nowait.domain.waiting.scheduler;

import com.nowait.domain.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
   웨이팅 호출 타임아웃 자동 취소 스케줄러
   - 기본: 매 분 0초마다 실행
   - waiting.call-timeout-minutes 분 이상 CALLED 상태인 웨이팅을 자동 CANCELLED 처리
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingTimeoutScheduler {

  private final WaitingService waitingService;

  @Scheduled(cron = "${waiting.timeout-check-cron:0 * * * * *}")
  public void cancelExpiredCalls() {
    try {
      int count = waitingService.cancelExpiredCalls();
      if (count > 0) {
        log.info("Scheduler executed. autoCancelled={}", count);
      }
    } catch (Exception e) {
      // 스케줄러는 예외 무한 전파되면 다음 실행도 막힐 수 있어 여기서 격리
      log.error("Failed to cancel expired calls", e);
    }
  }
}