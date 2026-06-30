package com.nowait.domain.reservation.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ReservationSyncWorker — 청크 배치 + 단건 폴백 + 멱등성(중복 토큰 DLQ 회피) + recoverInFlight 검증.
 */
@ExtendWith(MockitoExtension.class)
class ReservationSyncWorkerTest {

  @Mock StringRedisTemplate redis;
  @Mock ListOperations<String, String> listOps;
  @Mock ReservationSyncHandler handler;
  @Mock ReservationMetrics reservationMetrics;

  @InjectMocks ReservationSyncWorker worker;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "batchSize", 50);
    ReflectionTestUtils.setField(worker, "chunkSize", 2);
    when(redis.opsForList()).thenReturn(listOps);
  }

  private void givenPopped(String... tokens) {
    String first = tokens.length > 0 ? tokens[0] : null;
    String[] rest = new String[tokens.length];
    for (int i = 1; i < tokens.length; i++) {
      rest[i - 1] = tokens[i];
    }
    rest[tokens.length - 1] = null;
    when(listOps.rightPopAndLeftPush(ReservationRedisKeys.PENDING_SYNC, ReservationRedisKeys.PROCESSING))
        .thenReturn(first, rest);
  }

  @Test
  @DisplayName("배치 성공: chunkSize 단위로 분할 호출하고 청크 전체를 ack 한다")
  void batchSuccess_splitsIntoChunksAndAcksAll() {
    givenPopped("t1", "t2", "t3");
    doNothing().when(handler).syncBatch(any());

    worker.poll();

    ArgumentCaptor<List<String>> chunkCaptor = ArgumentCaptor.forClass(List.class);
    verify(handler, times(2)).syncBatch(chunkCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(chunkCaptor.getAllValues().get(0)).containsExactly("t1", "t2");
    org.assertj.core.api.Assertions.assertThat(chunkCaptor.getAllValues().get(1)).containsExactly("t3");

    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t1");
    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t2");
    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t3");
    verify(reservationMetrics).batchProcessed(2);
    verify(reservationMetrics).batchProcessed(1);
    verify(handler, never()).sync(anyString());
    verify(reservationMetrics, never()).batchFellBack();
  }

  @Test
  @DisplayName("ack(LREM)는 syncBatch 성공 이후에만 일어난다")
  void ackHappensOnlyAfterPersist() {
    givenPopped("t1");
    doNothing().when(handler).syncBatch(any());

    worker.poll();

    InOrder order = inOrder(handler, listOps);
    order.verify(handler).syncBatch(any());
    order.verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t1");
  }

  @Test
  @DisplayName("배치 실패: 단건으로 폴백하고 성공 토큰은 ack 한다")
  void batchFailure_fallsBackToSingleAndAcksSuccess() {
    givenPopped("t1", "t2");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    when(handler.sync("t1")).thenReturn(true);
    when(handler.sync("t2")).thenReturn(true);

    worker.poll();

    verify(reservationMetrics).batchFellBack();
    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t1");
    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t2");
    verify(listOps, never()).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), anyString());
  }

  @Test
  @DisplayName("폴백 중 UNIQUE 충돌 + DB row 존재 → 멱등 ack, DLQ 없음")
  void fallbackDuplicateKeyWithExistingRow_idempotentAck() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    when(handler.sync("t1")).thenThrow(new DataIntegrityViolationException(
        "Duplicate entry 'tok' for key 'reservation.UKxxx'"));
    when(handler.existsByReservationToken("t1")).thenReturn(true);

    worker.poll();

    verify(reservationMetrics).idempotentDuplicateSkipped();
    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t1");
    verify(listOps, never()).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), anyString());
    verify(reservationMetrics, never()).deadLettered();
  }

  @Test
  @DisplayName("폴백 중 UNIQUE 충돌처럼 보이나 DB row 없음 → DLQ 유지")
  void fallbackDuplicateKeyButRowMissing_deadLetters() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    when(handler.sync("t1")).thenThrow(new DataIntegrityViolationException("Duplicate entry ..."));
    when(handler.existsByReservationToken("t1")).thenReturn(false);

    worker.poll();

    verify(reservationMetrics, never()).idempotentDuplicateSkipped();
    verify(reservationMetrics).persistFailed();
    verify(reservationMetrics).batchFailed();
    verify(reservationMetrics).deadLettered();
    verify(listOps).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), startsWith("t1|"));
  }

  @Test
  @DisplayName("폴백 중 중복키가 아닌 일반 예외 → DLQ 유지 (멱등 판정 안 함)")
  void fallbackNonDuplicateException_deadLetters() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    when(handler.sync("t1")).thenThrow(new RuntimeException("connection reset"));

    worker.poll();

    verify(handler, never()).existsByReservationToken(anyString());
    verify(reservationMetrics).deadLettered();
    verify(listOps).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), startsWith("t1|"));
  }

  @Test
  @DisplayName("[recover] in-flight 토큰이 이미 DB에 있으면 requeue 생략 + 제거")
  void recoverInFlight_skipsTokenAlreadyInDb() {
    when(listOps.size(ReservationRedisKeys.PROCESSING)).thenReturn(1L);
    when(listOps.rightPopAndLeftPush(ReservationRedisKeys.PROCESSING, ReservationRedisKeys.PENDING_SYNC))
        .thenReturn("t1");
    when(handler.existsByReservationToken("t1")).thenReturn(true);

    ReflectionTestUtils.invokeMethod(worker, "recoverInFlight");

    verify(listOps).remove(ReservationRedisKeys.PENDING_SYNC, 1, "t1");
    verify(reservationMetrics).recoverSkippedExisting();
    verify(reservationMetrics, never()).recoveredInflight();
  }

  @Test
  @DisplayName("[recover] DB에 없는 토큰은 pending으로 복구")
  void recoverInFlight_requeuesTokenNotInDb() {
    when(listOps.size(ReservationRedisKeys.PROCESSING)).thenReturn(1L);
    when(listOps.rightPopAndLeftPush(ReservationRedisKeys.PROCESSING, ReservationRedisKeys.PENDING_SYNC))
        .thenReturn("t1");
    when(handler.existsByReservationToken("t1")).thenReturn(false);

    ReflectionTestUtils.invokeMethod(worker, "recoverInFlight");

    verify(reservationMetrics).recoveredInflight();
    verify(reservationMetrics, never()).recoverSkippedExisting();
    verify(listOps, never()).remove(eq(ReservationRedisKeys.PENDING_SYNC), anyLong(), anyString());
  }

  @Test
  @DisplayName("큐가 비어 있으면 아무 작업도 하지 않는다")
  void emptyQueue_noop() {
    when(listOps.rightPopAndLeftPush(ReservationRedisKeys.PENDING_SYNC, ReservationRedisKeys.PROCESSING))
        .thenReturn(null);

    worker.poll();

    verify(handler, never()).syncBatch(any());
    verify(handler, never()).sync(anyString());
  }
}
