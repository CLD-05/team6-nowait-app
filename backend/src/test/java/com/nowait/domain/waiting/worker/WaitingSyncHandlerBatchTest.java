package com.nowait.domain.waiting.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.monitoring.WaitingMetrics;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.domain.waiting.type.WaitingStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * WaitingSyncHandler.syncBatch 의 멱등/upsert/스킵 동작 검증.
 *
 * 검증 목표:
 *  - 신규 토큰은 saveAll 로 INSERT 된다.
 *  - 기존 토큰은 dirty update 만 하고 새 행을 만들지 않는다 (멱등 재처리 안전).
 *  - 같은 청크에 중복 토큰이 유입돼도 1회만 upsert 한다 (이중 INSERT 방지).
 *  - Redis Hash 가 사라진 토큰은 건너뛴다 (DB 반영/메트릭 없음).
 */
@ExtendWith(MockitoExtension.class)
class WaitingSyncHandlerBatchTest {

  @Mock WaitingRepository waitingRepository;
  @Mock WaitingRedisLuaExecutor waitingRedis;
  @Mock WaitingMetrics waitingMetrics;

  @InjectMocks WaitingSyncHandler handler;

  private static final long REGISTERED_AT = 1_000L;

  private WaitingTokenData data(WaitingStatus status, Long canceledAt) {
    // (userId, sessionId, restaurantId, waitingNumber, partySize, status, registeredAt, calledAt, enteredAt, canceledAt)
    return new WaitingTokenData(1L, 10L, 100L, 5, 2, status, REGISTERED_AT, null, null, canceledAt);
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<List<Waiting>> captureSaveAll() {
    return ArgumentCaptor.forClass(List.class);
  }

  @Test
  @DisplayName("신규 토큰은 saveAll 로 1건 INSERT 되고 persist 메트릭이 기록된다")
  void newToken_inserted() {
    when(waitingRepository.findByWaitingTokenIn(anyCollection())).thenReturn(List.of());
    when(waitingRedis.findByToken("t1")).thenReturn(data(WaitingStatus.WAITING, null));

    handler.syncBatch(List.of("t1"));

    ArgumentCaptor<List<Waiting>> captor = captureSaveAll();
    verify(waitingRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getWaitingToken()).isEqualTo("t1");
    verify(waitingMetrics).persistSucceeded(REGISTERED_AT);
  }

  @Test
  @DisplayName("기존 토큰은 update 만 하고 새 행을 INSERT 하지 않는다 (멱등 재처리 안전)")
  void existingToken_updatedNotInserted() {
    Waiting existing = Waiting.register("t1", 1L, 100L, 10L, 5, 2, LocalDateTime.now());
    when(waitingRepository.findByWaitingTokenIn(anyCollection())).thenReturn(List.of(existing));
    when(waitingRedis.findByToken("t1"))
        .thenReturn(data(WaitingStatus.CANCELLED, 2_000L));

    handler.syncBatch(List.of("t1"));

    // toInsert 가 비어 있으므로 saveAll 은 호출되지 않는다
    verify(waitingRepository, never()).saveAll(anyList());
    // 기존 엔티티가 Redis 상태로 갱신됐는지 확인
    assertThat(existing.getStatus()).isEqualTo(WaitingStatus.CANCELLED);
    verify(waitingMetrics).persistSucceeded(REGISTERED_AT);
  }

  @Test
  @DisplayName("같은 청크에 중복 토큰이 들어와도 dedup 되어 1건만 INSERT 한다")
  void duplicateTokenInChunk_dedupedToSingleInsert() {
    when(waitingRepository.findByWaitingTokenIn(anyCollection())).thenReturn(List.of());
    when(waitingRedis.findByToken("t1")).thenReturn(data(WaitingStatus.WAITING, null));

    handler.syncBatch(List.of("t1", "t1", "t1"));

    ArgumentCaptor<List<Waiting>> captor = captureSaveAll();
    verify(waitingRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    // dedup 후 distinct 토큰 1개 → Redis 조회도 1회
    verify(waitingRedis).findByToken("t1");
  }

  @Test
  @DisplayName("Redis Hash 가 사라진 토큰은 건너뛴다 (INSERT/persist 메트릭 없음)")
  void missingRedisHash_skipped() {
    when(waitingRepository.findByWaitingTokenIn(anyCollection())).thenReturn(List.of());
    when(waitingRedis.findByToken("t1")).thenReturn(null);

    handler.syncBatch(List.of("t1"));

    verify(waitingRepository, never()).saveAll(anyList());
    verify(waitingMetrics, never()).persistSucceeded(anyLong());
  }
}
