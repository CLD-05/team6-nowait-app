package com.nowait.domain.reservation.monitoring;

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
 * ReservationMetrics.rejected 의 사유별 집계 동작 검증.
 *
 * 검증 목표:
 *  - nowait_reservation_rejected_total{reason=ENUM} 가 "모든" 비즈니스 거절에 대해 증가하는지
 *    (기존엔 INTERNAL_SERVER_ERROR 만 failure 로 집계되고 나머지 4xx 는 전혀 안 잡혔다).
 *  - reason 라벨이 ErrorCode enum(name) 기반인지 (메시지 기반 금지).
 *  - 시스템 오류만 reservation_failure 로 집계되는 기존 동작이 유지되는지.
 */
@ExtendWith(MockitoExtension.class)
class ReservationMetricsTest {

  private SimpleMeterRegistry registry;

  @Mock StringRedisTemplate redis;

  private ReservationMetrics metrics;

  private static final String REJECTED = "nowait.reservation.rejected";
  private static final String FAILURE = "nowait.reservation.failure";

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new ReservationMetrics(registry, redis);
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
  @DisplayName("슬롯 만석(SLOT_FULL) 거절도 reason 라벨로 집계되고, 실패 카운터는 오르지 않는다")
  void slotFullIncrementsTaggedNotFailure() {
    metrics.rejected(ErrorCode.SLOT_FULL);

    assertThat(rejected(ErrorCode.SLOT_FULL)).isEqualTo(1.0);
    // 정상 비즈니스 거절 → 실패율 SLI(reservation_failure)는 오르지 않아야 한다
    assertThat(counter(FAILURE)).isEqualTo(0.0);
  }

  @Test
  @DisplayName("중복 예약(DUPLICATE_RESERVATION) 거절도 reason 라벨로 집계된다")
  void duplicateReservationIncrementsTagged() {
    metrics.rejected(ErrorCode.DUPLICATE_RESERVATION);

    assertThat(rejected(ErrorCode.DUPLICATE_RESERVATION)).isEqualTo(1.0);
    assertThat(counter(FAILURE)).isEqualTo(0.0);
  }

  @Test
  @DisplayName("INTERNAL_SERVER_ERROR 는 reason 태그 카운터와 실패 카운터 모두 증가한다")
  void internalErrorIncrementsBoth() {
    metrics.rejected(ErrorCode.INTERNAL_SERVER_ERROR);

    assertThat(rejected(ErrorCode.INTERNAL_SERVER_ERROR)).isEqualTo(1.0);
    assertThat(counter(FAILURE)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("reason 라벨 값은 ErrorCode enum name 이며 메시지가 아니다")
  void reasonLabelIsEnumBasedNotMessageBased() {
    metrics.rejected(ErrorCode.NOT_OPERATING_TIME);

    Counter c = registry.find(REJECTED).tag("reason", ErrorCode.NOT_OPERATING_TIME.name()).counter();
    assertThat(c).isNotNull();
    assertThat(c.getId().getTag("reason")).isEqualTo("NOT_OPERATING_TIME");
    assertThat(c.getId().getTag("reason")).isNotEqualTo(ErrorCode.NOT_OPERATING_TIME.getMessage());
  }

  @Test
  @DisplayName("같은 사유가 여러 번 거절되면 해당 reason 카운터가 누적된다")
  void sameReasonAccumulates() {
    metrics.rejected(ErrorCode.SLOT_FULL);
    metrics.rejected(ErrorCode.SLOT_FULL);

    assertThat(rejected(ErrorCode.SLOT_FULL)).isEqualTo(2.0);
  }
}
