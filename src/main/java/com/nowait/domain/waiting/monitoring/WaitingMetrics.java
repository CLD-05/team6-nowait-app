package com.nowait.domain.waiting.monitoring;

import com.nowait.domain.waiting.redis.WaitingRedisKeys;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
 * 웨이팅 도메인 큐/대기 메트릭을 Micrometer 에 등록 (ReservationMetrics와 동일한 패턴).
 *
 * 노출 경로 (Actuator/Prometheus):
 *   GET /actuator/metrics/waiting.queue.pending
 *   GET /actuator/metrics/waiting.queue.processing
 *   GET /actuator/metrics/waiting.queue.deadletter
 *   GET /actuator/metrics/waiting.active.sessions
 *
 * Gauge 는 scrape 시점에 Redis 를 호출해서 현재 값을 반환한다.
 * API/Worker Pod 어디서든 활성화 — 둘 다 같은 Redis 를 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingMetrics {

  private final MeterRegistry meterRegistry;
  private final StringRedisTemplate redis;

  @PostConstruct
  void register() {
    meterRegistry.gauge("waiting.queue.pending", this,
        m -> safeLLen(WaitingRedisKeys.PENDING_SYNC));
    meterRegistry.gauge("waiting.queue.processing", this,
        m -> safeLLen(WaitingRedisKeys.PROCESSING));
    meterRegistry.gauge("waiting.queue.deadletter", this,
        m -> safeLLen(WaitingRedisKeys.DEAD_LETTER));
    meterRegistry.gauge("waiting.active.sessions", this,
        m -> safeSCard(WaitingRedisKeys.ACTIVE_SESSIONS));

    log.info("WaitingMetrics registered: pending/processing/deadletter/active-sessions gauges");
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

  private double safeSCard(String key) {
    try {
      Long size = redis.opsForSet().size(key);
      return size == null ? 0.0 : size.doubleValue();
    } catch (Exception e) {
      log.warn("Failed to read SCARD {} for metric: {}", key, e.getMessage());
      return Double.NaN;
    }
  }
}
