package com.nowait.domain.waiting.service;

import com.nowait.domain.notification.service.NotificationService;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.service.RestaurantHourService;
import com.nowait.domain.restaurant.service.RestaurantService;
import com.nowait.domain.waiting.dto.WaitingResponse;
import com.nowait.domain.waiting.monitoring.WaitingMetrics;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.repository.WaitingCallLogRepository;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.domain.waiting.type.WaitingStatus;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * polling 전용 토큰 단위 조회 getMyWaitingByToken 의 동작 검증.
 * Redis 만 사용하고(DB 미사용) 소유자 검증·미존재 처리가 정확한지 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class WaitingServiceTokenLookupTest {

  @Mock WaitingRedisLuaExecutor waitingRedis;
  @Mock WaitingMetrics waitingMetrics;

  // 아래 의존성은 본 메서드에서 사용하지 않지만 생성자 주입을 위해 모킹
  @Mock WaitingSessionService waitingSessionService;
  @Mock RestaurantRepository restaurantRepository;
  @Mock WaitingRepository waitingRepository;
  @Mock WaitingCallLogRepository waitingCallLogRepository;
  @Mock NotificationService notificationService;
  @Mock RestaurantHourService restaurantHourService;
  @Mock RestaurantService restaurantService;

  @InjectMocks WaitingService waitingService;

  private WaitingTokenData data(long userId) {
    return new WaitingTokenData(
        userId, 100L, 200L, 3, 2, WaitingStatus.WAITING,
        1_700_000_000_000L, null, null, null);
  }

  @Test
  @DisplayName("토큰 조회 성공 시 aheadCount를 포함한 응답을 반환하고 polling 지표를 올린다")
  void returnsResponseWithAheadCount() {
    when(waitingRedis.findByToken("tok")).thenReturn(data(7L));
    when(waitingRedis.aheadCount(100L, "tok")).thenReturn(4L);

    WaitingResponse res = waitingService.getMyWaitingByToken("tok", 7L);

    assertThat(res.waitingToken()).isEqualTo("tok");
    assertThat(res.userId()).isEqualTo(7L);
    assertThat(res.aheadCount()).isEqualTo(4L);
    verify(waitingMetrics).pollingObserved();
  }

  @Test
  @DisplayName("다른 사용자의 토큰을 조회하면 ACCESS_DENIED")
  void deniesOtherUsersToken() {
    when(waitingRedis.findByToken("tok")).thenReturn(data(7L));

    assertThatThrownBy(() -> waitingService.getMyWaitingByToken("tok", 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("존재하지 않는 토큰을 조회하면 WAITING_NOT_FOUND")
  void notFoundForUnknownToken() {
    when(waitingRedis.findByToken("missing")).thenReturn(null);

    assertThatThrownBy(() -> waitingService.getMyWaitingByToken("missing", 7L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.WAITING_NOT_FOUND);
  }
}
