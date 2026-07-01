package com.nowait.domain.slot.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nowait.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Worker sync 재조정 전용 clamp 메서드 검증.
 *
 * 핵심: decreaseForSync/increaseForSync 는 정원 경계(0/총원)에서 예외를 던지지 않는다.
 * 던지면 @Transactional(REQUIRED) 참여 트랜잭션이 rollback-only 로 마킹돼 상위 커밋이
 * UnexpectedRollbackException 으로 실패하고 정상 토큰까지 DLQ 로 가기 때문이다.
 */
class SlotSyncClampTest {

  private Slot slot(int totalCount) {
    return Slot.builder().totalCount(totalCount).build();
  }

  @Test
  @DisplayName("decreaseForSync: remainCount 0 에서 호출해도 예외 없이 0 을 유지한다")
  void decreaseForSync_atZero_doesNotThrowAndClamps() {
    Slot slot = slot(1);
    slot.decreaseForSync(); // 1 -> 0

    assertThat(slot.getRemainCount()).isZero();
    assertThatCode(slot::decreaseForSync).doesNotThrowAnyException(); // 0 에서 재차감
    assertThat(slot.getRemainCount()).isZero();
  }

  @Test
  @DisplayName("increaseForSync: remainCount == totalCount 에서 호출해도 예외 없이 총원을 유지한다")
  void increaseForSync_atTotal_doesNotThrowAndClamps() {
    Slot slot = slot(2); // remain = total = 2

    assertThatCode(slot::increaseForSync).doesNotThrowAnyException();
    assertThat(slot.getRemainCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("기존 decrease()/increase() 는 경계에서 여전히 BusinessException 을 던진다(API 경로 정책 유지)")
  void throwingVariants_stillGuardBoundaries() {
    Slot full = slot(1);
    full.decrease(); // 1 -> 0
    assertThatThrownBy(full::decrease).isInstanceOf(BusinessException.class);

    Slot atTotal = slot(1); // remain == total
    assertThatThrownBy(atTotal::increase).isInstanceOf(BusinessException.class);
  }
}
