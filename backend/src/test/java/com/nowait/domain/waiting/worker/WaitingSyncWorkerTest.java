package com.nowait.domain.waiting.worker;

import static org.mockito.ArgumentMatchers.any;
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

import com.nowait.domain.waiting.monitoring.WaitingMetrics;
import com.nowait.domain.waiting.redis.WaitingRedisKeys;
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
 * WaitingSyncWorker.poll 의 청크 분할 + 배치/폴백 + ack 순서 검증.
 *
 * 검증 목표:
 *  - batchSize 만큼 pop 한 토큰을 chunkSize 단위로 나눠 syncBatch 를 호출한다.
 *  - 배치 성공 시 청크 전체를 ack(LREM) 하고 batchProcessed 메트릭을 올린다.
 *  - 배치 실패 시 단건(sync)으로 폴백하고, 성공 토큰은 ack, 실패 토큰은 dead-letter.
 *  - ack(LREM)는 반드시 DB 영속화(syncBatch) "이후"에만 일어난다.
 */
@ExtendWith(MockitoExtension.class)
class WaitingSyncWorkerTest {

  @Mock StringRedisTemplate redis;
  @Mock ListOperations<String, String> listOps;
  @Mock WaitingSyncHandler handler;
  @Mock WaitingMetrics waitingMetrics;

  @InjectMocks WaitingSyncWorker worker;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "batchSize", 50);
    ReflectionTestUtils.setField(worker, "chunkSize", 2);
    when(redis.opsForList()).thenReturn(listOps);
  }

  /* pending-sync → processing 으로 토큰들을 차례로 내보내고 마지막에 null(큐 빔) */
  private void givenPopped(String... tokens) {
    String first = tokens.length > 0 ? tokens[0] : null;
    String[] rest = new String[tokens.length]; // tokens[1..], 마지막은 null
    for (int i = 1; i < tokens.length; i++) {
      rest[i - 1] = tokens[i];
    }
    rest[tokens.length - 1] = null;
    when(listOps.rightPopAndLeftPush(WaitingRedisKeys.PENDING_SYNC, WaitingRedisKeys.PROCESSING))
        .thenReturn(first, rest);
  }

  @Test
  @DisplayName("배치 성공: chunkSize 단위로 분할 호출하고 청크 전체를 ack 한다")
  void batchSuccess_splitsIntoChunksAndAcksAll() {
    givenPopped("t1", "t2", "t3");
    doNothing().when(handler).syncBatch(any());

    worker.poll();

    // 3토큰, chunkSize=2 → [t1,t2], [t3] 두 청크
    ArgumentCaptor<List<String>> chunkCaptor = ArgumentCaptor.forClass(List.class);
    verify(handler, times(2)).syncBatch(chunkCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(chunkCaptor.getAllValues().get(0)).containsExactly("t1", "t2");
    org.assertj.core.api.Assertions.assertThat(chunkCaptor.getAllValues().get(1)).containsExactly("t3");

    // 청크 전체 ack(LREM)
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t1");
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t2");
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t3");
    verify(waitingMetrics).batchProcessed(2);
    verify(waitingMetrics).batchProcessed(1);
    // 배치 성공 경로에서는 폴백/단건 sync 가 일어나지 않는다
    verify(handler, never()).sync(anyString());
    verify(waitingMetrics, never()).batchFellBack();
  }

  @Test
  @DisplayName("ack(LREM)는 syncBatch 성공 이후에만 일어난다 (DB 영속화 전 ack 금지)")
  void ackHappensOnlyAfterPersist() {
    givenPopped("t1");
    doNothing().when(handler).syncBatch(any());

    worker.poll();

    InOrder order = inOrder(handler, listOps);
    order.verify(handler).syncBatch(any());
    order.verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t1");
  }

  @Test
  @DisplayName("배치 실패: 단건으로 폴백하고 성공 토큰은 ack 한다")
  void batchFailure_fallsBackToSingleAndAcksSuccess() {
    givenPopped("t1", "t2");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    when(handler.sync("t1")).thenReturn(true);
    when(handler.sync("t2")).thenReturn(true);

    worker.poll();

    verify(waitingMetrics).batchFellBack();
    verify(handler).sync("t1");
    verify(handler).sync("t2");
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t1");
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t2");
    // 둘 다 성공 → dead-letter 없음
    verify(listOps, never()).leftPush(eq(WaitingRedisKeys.DEAD_LETTER), anyString());
  }

  @Test
  @DisplayName("폴백에서도 실패한 토큰은 dead-letter 로 보내고 메트릭을 올린다")
  void fallbackFailure_movesToDeadLetter() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    when(handler.sync("t1")).thenReturn(false); // 일시적 실패 → 재시도/DLQ 대상

    worker.poll();

    verify(waitingMetrics).batchFellBack();
    verify(waitingMetrics).persistFailed();
    verify(waitingMetrics).batchFailed();
    // dead-letter 이동: processing 에서 제거 + DLQ push + 메트릭
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t1");
    verify(listOps).leftPush(eq(WaitingRedisKeys.DEAD_LETTER), startsWith("t1|"));
    verify(waitingMetrics).deadLettered();
  }

  @Test
  @DisplayName("큐가 비어 있으면 아무 작업도 하지 않는다")
  void emptyQueue_noop() {
    when(listOps.rightPopAndLeftPush(WaitingRedisKeys.PENDING_SYNC, WaitingRedisKeys.PROCESSING))
        .thenReturn(null);

    worker.poll();

    verify(handler, never()).syncBatch(any());
    verify(handler, never()).sync(anyString());
  }

  /* ===== 멱등성 보완: 중복 waitingToken UNIQUE 충돌 → DLQ 회피 (문서 C/D/E) ===== */

  @Test
  @DisplayName("[C] UNIQUE 충돌 + DB에 row 존재 → 멱등 성공으로 ack, DLQ 보내지 않음")
  void duplicateKeyViolationWithExistingRow_idempotentAck() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    doThrow(new DataIntegrityViolationException(
        "Duplicate entry 'tok' for key 'waiting.UKinb630r07htm38oi45nkoqpen'"))
        .when(handler).sync("t1");
    when(handler.existsByWaitingToken("t1")).thenReturn(true);

    worker.poll();

    verify(waitingMetrics).idempotentDuplicateSkipped();
    verify(listOps).remove(WaitingRedisKeys.PROCESSING, 1, "t1"); // ack
    // DLQ 로 보내지 않는다
    verify(listOps, never()).leftPush(eq(WaitingRedisKeys.DEAD_LETTER), anyString());
    verify(waitingMetrics, never()).deadLettered();
    verify(waitingMetrics, never()).persistFailed();
  }

  @Test
  @DisplayName("[E] UNIQUE 충돌처럼 보이나 DB row 없음 → 멱등 처리 안 함, DLQ 유지")
  void duplicateKeyViolationButRowMissing_deadLetters() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    doThrow(new DataIntegrityViolationException("Duplicate entry 'tok' for key '...'"))
        .when(handler).sync("t1");
    when(handler.existsByWaitingToken("t1")).thenReturn(false);

    worker.poll();

    verify(waitingMetrics, never()).idempotentDuplicateSkipped();
    verify(waitingMetrics).persistFailed();
    verify(waitingMetrics).deadLettered();
    verify(listOps).leftPush(eq(WaitingRedisKeys.DEAD_LETTER), startsWith("t1|"));
  }

  @Test
  @DisplayName("[D] 중복키가 아닌 일반 DB 예외 → 멱등 처리 안 함, DLQ 유지")
  void nonDuplicateException_deadLetters() {
    givenPopped("t1");
    doThrow(new RuntimeException("boom")).when(handler).syncBatch(any());
    doThrow(new RuntimeException("connection reset")).when(handler).sync("t1");

    worker.poll();

    verify(waitingMetrics, never()).idempotentDuplicateSkipped();
    // 중복키가 아니므로 existsByWaitingToken 으로 멱등 판정조차 하지 않는다
    verify(handler, never()).existsByWaitingToken(anyString());
    verify(waitingMetrics).deadLettered();
    verify(listOps).leftPush(eq(WaitingRedisKeys.DEAD_LETTER), startsWith("t1|"));
  }

  /* ===== recoverInFlight 멱등성 보완 (문서 F) ===== */

  @Test
  @DisplayName("[F] 복구 시 in-flight 토큰이 이미 DB에 있으면 pending으로 되돌리지 않고 제거한다")
  void recoverInFlight_skipsTokenAlreadyInDb() {
    when(listOps.size(WaitingRedisKeys.PROCESSING)).thenReturn(1L);
    when(listOps.rightPopAndLeftPush(WaitingRedisKeys.PROCESSING, WaitingRedisKeys.PENDING_SYNC))
        .thenReturn("t1");
    when(handler.existsByWaitingToken("t1")).thenReturn(true);

    ReflectionTestUtils.invokeMethod(worker, "recoverInFlight");

    // pending 에 들어간 사본을 제거하고 skip 메트릭 증가
    verify(listOps).remove(WaitingRedisKeys.PENDING_SYNC, 1, "t1");
    verify(waitingMetrics).recoverSkippedExisting();
    verify(waitingMetrics, never()).recoveredInflight();
  }

  @Test
  @DisplayName("[F-2] 복구 시 DB에 없는 토큰은 pending으로 복구한다")
  void recoverInFlight_requeuesTokenNotInDb() {
    when(listOps.size(WaitingRedisKeys.PROCESSING)).thenReturn(1L);
    when(listOps.rightPopAndLeftPush(WaitingRedisKeys.PROCESSING, WaitingRedisKeys.PENDING_SYNC))
        .thenReturn("t1");
    when(handler.existsByWaitingToken("t1")).thenReturn(false);

    ReflectionTestUtils.invokeMethod(worker, "recoverInFlight");

    verify(waitingMetrics).recoveredInflight();
    verify(waitingMetrics, never()).recoverSkippedExisting();
    verify(listOps, never()).remove(eq(WaitingRedisKeys.PENDING_SYNC), org.mockito.ArgumentMatchers.anyLong(), anyString());
  }
}
