package com.nowait.domain.reservation.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.repository.ReservationRepository;
import com.nowait.domain.reservation.type.ReservationStatus;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.domain.slot.service.SlotService;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReservationSyncHandler.syncBatch 검증 — 특히 슬롯 PESSIMISTIC_WRITE 락 교착 방지를 위한
 * slotId 오름차순 처리, dedup, Redis hash 없는 토큰 skip.
 */
@ExtendWith(MockitoExtension.class)
class ReservationSyncHandlerBatchTest {

  @Mock ReservationRepository reservationRepository;
  @Mock UserRepository userRepository;
  @Mock RestaurantRepository restaurantRepository;
  @Mock SlotRepository slotRepository;
  @Mock SlotService slotService;
  @Mock ReservationRedisLuaExecutor reservationRedis;
  @Mock ReservationMetrics reservationMetrics;

  @InjectMocks ReservationSyncHandler handler;

  private ReservationTokenData data(long slotId) {
    // (userId, restaurantId, slotId, headcount, status, createdAt, reservationTime, visited/cancel/noshow/reject/reason)
    return new ReservationTokenData(1L, 10L, slotId, 2, ReservationStatus.CONFIRMED,
        1_000L, 2_000L, null, null, null, null, null);
  }

  private Slot slotWithId(long id) {
    Slot slot = org.mockito.Mockito.mock(Slot.class);
    lenient().when(slot.getId()).thenReturn(id);
    return slot;
  }

  @Test
  @DisplayName("슬롯 락 교착 방지: 토큰을 slotId 오름차순으로 처리해 decrease 가 slotId 순서로 호출된다")
  void processesInSlotIdOrderToPreventDeadlock() {
    // 입력 순서: t1(slot 5), t2(slot 2) — 정렬되면 slot 2 먼저 처리되어야 한다
    Slot slot5 = slotWithId(5L);
    Slot slot2 = slotWithId(2L);
    when(reservationRedis.findByToken("t1")).thenReturn(data(5L));
    when(reservationRedis.findByToken("t2")).thenReturn(data(2L));
    when(reservationRepository.findByReservationTokenIn(anyCollection())).thenReturn(List.of());
    when(userRepository.getReferenceById(anyLong())).thenReturn(org.mockito.Mockito.mock(User.class));
    when(restaurantRepository.getReferenceById(anyLong())).thenReturn(org.mockito.Mockito.mock(Restaurant.class));
    when(slotRepository.findById(5L)).thenReturn(Optional.of(slot5));
    when(slotRepository.findById(2L)).thenReturn(Optional.of(slot2));

    handler.syncBatch(List.of("t1", "t2"));

    // 핵심: slot 락은 항상 slotId 오름차순으로 획득되어야 한다 (decrease(2) → decrease(5))
    InOrder order = inOrder(slotService);
    order.verify(slotService).decrease(2L);
    order.verify(slotService).decrease(5L);
    verify(reservationRepository, times(2)).save(any());
  }

  @Test
  @DisplayName("같은 청크의 중복 토큰은 dedup 되어 1회만 처리한다")
  void duplicateTokenInChunk_dedup() {
    Slot slot3 = slotWithId(3L);
    when(reservationRedis.findByToken("t1")).thenReturn(data(3L));
    when(reservationRepository.findByReservationTokenIn(anyCollection())).thenReturn(List.of());
    when(userRepository.getReferenceById(anyLong())).thenReturn(org.mockito.Mockito.mock(User.class));
    when(restaurantRepository.getReferenceById(anyLong())).thenReturn(org.mockito.Mockito.mock(Restaurant.class));
    when(slotRepository.findById(3L)).thenReturn(Optional.of(slot3));

    handler.syncBatch(List.of("t1", "t1", "t1"));

    verify(reservationRedis, times(1)).findByToken("t1");
    verify(reservationRepository, times(1)).save(any());
    verify(slotService, times(1)).decrease(3L);
  }

  @Test
  @DisplayName("Redis hash 가 사라진 토큰은 건너뛴다 (저장/슬롯 미조정)")
  void missingRedisHash_skipped() {
    when(reservationRedis.findByToken("t1")).thenReturn(null);

    handler.syncBatch(List.of("t1"));

    verify(reservationRepository, never()).save(any());
    verify(slotService, never()).decrease(anyLong());
    verify(reservationMetrics, never()).persistSucceeded(anyLong());
  }
}
