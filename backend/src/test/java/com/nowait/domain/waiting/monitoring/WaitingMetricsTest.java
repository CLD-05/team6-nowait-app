package com.nowait.domain.waiting.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.nowait.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * WaitingMetrics.registerRejected 의 사유별 집계 동작 검증.
 *
 * 검증 목표:
 *  - 신규 태그 카운터 nowait_waiting_register_rejected_total{reason=ENUM} 가
 *    "모든" 비즈니스 거절에 대해 증가하는지 (기존 전용 카운터가 없던 사유 포함).
 *  - 기존 전용 카운터(duplicate/capacity/failure)가 하위호환을 위해 함께 유지되는지.
 *  - reason 라벨이 ErrorCode enum(name) 기반이며 메시지 기반이 아닌지.
 */
@ExtendWith(MockitoExtension.class)
class WaitingMetricsTest {

  // SimpleMeterRegistry: 실제 카운터 값을 읽어 검증할 수 있는 인메모리 레지스트리
  private SimpleMeterRegistry registry;

  // register() 의 gauge 람다는 scrape 시점에만 호출되므로 redis 상호작용은 발생하지 않는다.
  @Mock StringRedisTemplate redis;
  @Mock com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor waitingRedis;

  private WaitingMetrics metrics;

  private static final String REJECTED = "nowait.waiting.register.rejected";

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new WaitingMetrics(registry, redis, waitingRedis);
    metrics.register(); // @PostConstruct 수동 호출
  }

  private double rejected(ErrorCode code) {
    Counter c = registry.find(REJECTED).tag("reason", code.name()).counter();
    return c == null ? 0.0 : c.count();
  }

  private double counter(String name) {
    Counter c = registry.find(name).counter();
    return c == null ? 0.0 : c.count();
  }

  @Test
  @DisplayName("DUPLICATE_WAITING 거절 시 기존 중복 카운터와 신규 태그 카운터(reason=DUPLICATE_WAITING)가 함께 증가한다")
  void duplicateIncrementsBothOldAndTagged() {
    metrics.registerRejected(ErrorCode.DUPLICATE_WAITING);

    // 신규 태그 카운터 — reason 라벨은 enum name
    assertThat(rejected(ErrorCode.DUPLICATE_WAITING)).isEqualTo(1.0);
    // 기존 전용 카운터도 유지 (하위호환)
    assertThat(counter("nowait.duplicate.waiting.attempt")).isEqualTo(1.0);
  }

  @Test
  @DisplayName("WAITING_COUNT_EXCEEDED 거절 시 기존 capacity 카운터와 신규 태그 카운터가 함께 증가한다")
  void capacityIncrementsBothOldAndTagged() {
    metrics.registerRejected(ErrorCode.WAITING_COUNT_EXCEEDED);

    assertThat(rejected(ErrorCode.WAITING_COUNT_EXCEEDED)).isEqualTo(1.0);
    assertThat(counter("nowait.capacity.full")).isEqualTo(1.0);
  }

  @Test
  @DisplayName("기존 전용 카운터가 없던 사유(NOT_OPERATING_TIME)도 신규 태그 카운터에는 reason 라벨로 집계된다")
  void notOperatingTimeStillIncrementsTagged() {
    metrics.registerRejected(ErrorCode.NOT_OPERATING_TIME);

    assertThat(rejected(ErrorCode.NOT_OPERATING_TIME)).isEqualTo(1.0);
    // 정상 비즈니스 거절이므로 기존 실패 카운터는 증가하지 않아야 한다
    assertThat(counter("nowait.waiting.register.failure")).isEqualTo(0.0);
  }

  @Test
  @DisplayName("기존 전용 카운터가 없던 사유(RESTAURANT_NOT_OPEN)도 신규 태그 카운터에는 reason 라벨로 집계된다")
  void restaurantNotOpenStillIncrementsTagged() {
    metrics.registerRejected(ErrorCode.RESTAURANT_NOT_OPEN);

    assertThat(rejected(ErrorCode.RESTAURANT_NOT_OPEN)).isEqualTo(1.0);
    assertThat(counter("nowait.waiting.register.failure")).isEqualTo(0.0);
  }

  @Test
  @DisplayName("reason 라벨 값은 ErrorCode enum name 이며, 사람이 읽는 메시지가 아니다")
  void reasonLabelIsEnumBasedNotMessageBased() {
    metrics.registerRejected(ErrorCode.WAITING_SESSION_NOT_ACCEPTING);

    Counter c = registry.find(REJECTED)
        .tag("reason", ErrorCode.WAITING_SESSION_NOT_ACCEPTING.name())
        .counter();
    assertThat(c).isNotNull();
    assertThat(c.getId().getTag("reason")).isEqualTo("WAITING_SESSION_NOT_ACCEPTING");
    // 메시지 문자열이 라벨로 새지 않았는지 방어적으로 확인
    assertThat(c.getId().getTag("reason"))
        .isNotEqualTo(ErrorCode.WAITING_SESSION_NOT_ACCEPTING.getMessage());
  }

  @Test
  @DisplayName("같은 사유가 여러 번 거절되면 해당 reason 카운터가 누적된다")
  void sameReasonAccumulates() {
    metrics.registerRejected(ErrorCode.DUPLICATE_WAITING);
    metrics.registerRejected(ErrorCode.DUPLICATE_WAITING);
    metrics.registerRejected(ErrorCode.DUPLICATE_WAITING);

    assertThat(rejected(ErrorCode.DUPLICATE_WAITING)).isEqualTo(3.0);
  }
}
