package com.nowait.domain.reservation.worker;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ReservationSyncWorker 의 멱등성(중복 토큰 UNIQUE 충돌 → DLQ 회피) 및 recoverInFlight 보완 검증.
 * 예약 워커는 단건 처리(per-token) 구조이며, 멱등 ack 시 슬롯을 건드리지 않아야 한다.
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
    when(redis.opsForList()).thenReturn(listOps);
  }

  /* pending-sync → processing 으로 토큰들을 차례로 내보내고 마지막에 null(큐 빔) */
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
  @DisplayName("정상 처리: ack 만 하고 DLQ/멱등스킵 없음")
  void success_acksOnly() {
    givenPopped("t1");
    when(handler.sync("t1")).thenReturn(true);

    worker.poll();

    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t1");
    verify(listOps, never()).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), anyString());
    verify(reservationMetrics, never()).idempotentDuplicateSkipped();
  }

  @Test
  @DisplayName("UNIQUE 충돌 + DB row 존재 → 멱등 성공 ack, DLQ 없음, 슬롯 미조정")
  void duplicateKeyWithExistingRow_idempotentAck() {
    givenPopped("t1");
    when(handler.sync("t1")).thenThrow(new DataIntegrityViolationException(
        "Duplicate entry 'tok' for key 'reservation.UKxxx'"));
    when(handler.existsByReservationToken("t1")).thenReturn(true);

    worker.poll();

    verify(reservationMetrics).idempotentDuplicateSkipped();
    verify(listOps).remove(ReservationRedisKeys.PROCESSING, 1, "t1"); // ack
    verify(listOps, never()).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), anyString());
    verify(reservationMetrics, never()).deadLettered();
    verify(reservationMetrics, never()).persistFailed();
  }

  @Test
  @DisplayName("UNIQUE 충돌처럼 보이나 DB row 없음 → DLQ 유지")
  void duplicateKeyButRowMissing_deadLetters() {
    givenPopped("t1");
    when(handler.sync("t1")).thenThrow(new DataIntegrityViolationException("Duplicate entry ..."));
    when(handler.existsByReservationToken("t1")).thenReturn(false);

    worker.poll();

    verify(reservationMetrics, never()).idempotentDuplicateSkipped();
    verify(reservationMetrics).persistFailed();
    verify(reservationMetrics).deadLettered();
    verify(listOps).leftPush(eq(ReservationRedisKeys.DEAD_LETTER), startsWith("t1|"));
  }

  @Test
  @DisplayName("중복키가 아닌 일반 예외 → DLQ 유지 (멱등 판정조차 안 함)")
  void nonDuplicateException_deadLetters() {
    givenPopped("t1");
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
}
