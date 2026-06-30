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

  /*
   * BusinessException 발생 시 호출.
   *
   * nowait_reservation_rejected_total{reason="<ERROR_CODE>"}:
   *   k6 가 보는 예약 생성 4xx 응답을 "백엔드 비즈니스 거절 사유"별로 분해하기 위한 태그 카운터.
   *   슬롯 만석(SLOT_FULL)/중복(DUPLICATE_RESERVATION)/영업시간 외(NOT_OPERATING_TIME)/
   *   인원 범위(INVALID_MIN/MAX_HEADCOUNT) 등 모든 거절을 reason 라벨로 분리해
   *   "예약 4xx 가 왜 발생하는지" Grafana 에서 설명 가능하게 한다.
   *   ⚠️ reason 라벨은 반드시 ErrorCode enum(code.name())만 사용한다.
   *      자유 형식 예외 메시지를 라벨로 쓰면 Prometheus 라벨 카디널리티가 폭발한다.
   *
   * 기존 동작 유지: 시스템 오류(INTERNAL_SERVER_ERROR)만 reservation_failure 로 집계한다
   *   (영업시간/정원/휴무일 등 정상 비즈니스 거절은 실패율 SLI 에 포함하지 않음).
   */
  public void rejected(ErrorCode code) {
    // (신규) 모든 비즈니스 거절을 사유별로 집계 — k6 4xx 분해용
    meterRegistry.counter("nowait.reservation.rejected", "reason", code.name()).increment();

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
