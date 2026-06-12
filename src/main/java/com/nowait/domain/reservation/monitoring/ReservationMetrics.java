package com.nowait.domain.reservation.monitoring;

import com.nowait.domain.reservation.redis.ReservationRedisKeys;
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

    log.info("ReservationMetrics registered: pending/processing/deadletter/noshow gauges");
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
