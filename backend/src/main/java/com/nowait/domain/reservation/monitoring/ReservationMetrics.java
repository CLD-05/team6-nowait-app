package com.nowait.domain.reservation.monitoring;

import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import com.nowait.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
 * 예약 도메인 큐/대기 메트릭을 Micrometer 에 등록.
 *
 * 노출 경로 (Actuator):
 *   GET /actuator/metrics/reservation.queue.pending
 *   GET /actuator/metrics/reservation.queue.processing
 *   GET /actuator/metrics/reservation.queue.deadletter
 *   GET /actuator/metrics/reservation.noshow.candidates
 *
 * Gauge 는 scrape 시점에 Redis 를 호출해서 현재 값을 반환한다.
 * API/Worker Pod 어디서든 활성화 — 둘 다 같은 Redis 를 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMetrics {

  private final MeterRegistry meterRegistry;
  private final StringRedisTemplate redis;

  /*
   * 예약 생성 결과 카운터 (nowait_* 네임스페이스):
   *   nowait_reservation_success_total   예약 성공
   *   nowait_reservation_failure_total   예약 시스템 실패 (INTERNAL_SERVER_ERROR / 예기치 못한 예외)
   * 영업시간/정원/휴무일 등 정상적인 비즈니스 거절은 실패로 집계하지 않는다.
   */
  private Counter reservationSuccess;
  private Counter reservationFailure;

  @PostConstruct
  void register() {
    meterRegistry.gauge("reservation.queue.pending", this,
        m -> safeLLen(ReservationRedisKeys.PENDING_SYNC));
    meterRegistry.gauge("reservation.queue.processing", this,
        m -> safeLLen(ReservationRedisKeys.PROCESSING));
    meterRegistry.gauge("reservation.queue.deadletter", this,
        m -> safeLLen(ReservationRedisKeys.DEAD_LETTER));
    meterRegistry.gauge("reservation.noshow.candidates", this,
        m -> safeZCard(ReservationRedisKeys.NOSHOW_CANDIDATES));

    reservationSuccess = meterRegistry.counter("nowait.reservation.success");
    reservationFailure = meterRegistry.counter("nowait.reservation.failure");

    log.info("ReservationMetrics registered: queue gauges + nowait reservation counters");
  }

  public void created() {
    reservationSuccess.increment();
  }

  /* BusinessException 발생 시 — 시스템 오류만 실패로 집계 */
  public void rejected(ErrorCode code) {
    if (code == ErrorCode.INTERNAL_SERVER_ERROR) {
      reservationFailure.increment();
    }
  }

  /* 비즈니스 예외가 아닌 예기치 못한 시스템 실패 */
  public void systemFailed() {
    reservationFailure.increment();
  }

  private double safeLLen(String key) {
    try {
      Long size = redis.opsForList().size(key);
      return size == null ? 0.0 : size.doubleValue();
    } catch (Exception e) {
      log.warn("Failed to read LLEN {} for metric: {}", key, e.getMessage());
      return Double.NaN;
    }
  }

  private double safeZCard(String key) {
    try {
      Long size = redis.opsForZSet().zCard(key);
      return size == null ? 0.0 : size.doubleValue();
    } catch (Exception e) {
      log.warn("Failed to read ZCARD {} for metric: {}", key, e.getMessage());
      return Double.NaN;
    }
  }
}
