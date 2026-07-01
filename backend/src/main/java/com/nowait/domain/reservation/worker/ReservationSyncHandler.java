package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.repository.ReservationRepository;
import com.nowait.domain.reservation.type.RejectionReason;
import com.nowait.domain.reservation.type.ReservationStatus;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.domain.slot.service.SlotService;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Worker 핸들러 — token 1건의 Redis 상태를 DB 에 멱등하게 반영한다.
 *
 * 원리:
 *   - Redis Hash = source of truth
 *   - 메시지의 의도와 무관하게 항상 Hash 의 현재 상태로 upsert
 *   - 메시지 중복/순서뒤바뀜에도 결과 동일 (idempotent)
 *
 * Slot.remainCount 멱등 처리:
 *   - 신규 INSERT (CONFIRMED)        → slot.decrease()
 *   - 기존 활성 → CANCELLED          → slot.increase()  (취소 시 정원 복구)
 *   - 기존 활성 → VISITED / NO_SHOW  → 변동 없음        (점유 확정 / 시간 만료)
 *   - 같은 상태 재진입               → 변동 없음        (멱등)
 */
@Slf4j
@Component
@Profile("reservation-worker")
@RequiredArgsConstructor
public class ReservationSyncHandler {

  private final ReservationRepository reservationRepository;
  private final UserRepository userRepository;
  private final RestaurantRepository restaurantRepository;
  private final SlotRepository slotRepository;
  private final SlotService slotService;
  private final ReservationRedisLuaExecutor reservationRedis;
  private final ReservationMetrics reservationMetrics;

  /*
   * Worker 멱등성: 해당 reservationToken 이 이미 DB 에 저장됐는지 확인한다.
   * UNIQUE 충돌(동시 처리) 또는 재기동 복구 시 "이미 처리됨"을 판정하는 데 사용한다.
   * 독립적인 readOnly 트랜잭션 — 롤백된 저장 트랜잭션과 무관하게 재조회한다.
   */
  @Transactional(readOnly = true)
  public boolean existsByReservationToken(String token) {
    return reservationRepository.existsByReservationToken(token);
  }

  /*
   * @return true = 정상 처리 (재시도 X), false = 일시적 오류 (재시도 대상)
   */
  @Transactional
  public boolean sync(String token) {
    ReservationTokenData data = reservationRedis.findByToken(token);

    /* Redis Hash 가 사라진 경우 (TTL 만료 등) → 이미 만료된 옛 메시지, drop */
    if (data == null) {
      log.warn("Sync skipped — Redis hash missing. token={}", token);
      return true;
    }

    LocalDateTime visitedAt = toLdtNullable(data.visitedAt());
    LocalDateTime canceledAt = toLdtNullable(data.canceledAt());
    LocalDateTime noShowAt = toLdtNullable(data.noShowAt());
    LocalDateTime rejectedAt = toLdtNullable(data.rejectedAt());
    RejectionReason rejectionReason = data.rejectionReason() == null ? null
        : RejectionReason.valueOf(data.rejectionReason());

    reservationRepository.findByReservationToken(token).ifPresentOrElse(
        existing -> applyUpdate(existing, data, visitedAt, canceledAt, noShowAt, rejectedAt, rejectionReason),
        () -> applyInsert(token, data, visitedAt, canceledAt, noShowAt, rejectedAt, rejectionReason)
    );

    reservationMetrics.persistSucceeded(data.createdAt());
    log.debug("Synced. token={} status={}", token, data.status());
    return true;
  }

  /*
   * 청크 배치 동기화 — 청크 내 토큰들을 "한 트랜잭션"으로 처리한다.
   *
   * ⚠️ 슬롯 PESSIMISTIC_WRITE 락 교착 방지:
   *   applyInsert/applyUpdate 는 slotService.decreaseForSync/increaseForSync 로 슬롯 행
   *   락을 잡는다. 한 청크가 여러 슬롯을 건드리면 동시 청크 트랜잭션들이 서로 다른 순서로
   *   락을 잡아 deadlock 이 날 수 있다. 따라서 토큰을 slotId 오름차순으로 정렬해 "모든 배치
   *   트랜잭션이 동일한 순서로 슬롯 락을 획득"하도록 보장한다.
   *
   * 멱등/안전:
   *   - 토큰 dedup, findByReservationTokenIn 일괄 조회로 upsert (중복 INSERT 없음).
   *   - 슬롯 차감/복구가 같은 트랜잭션에 묶이므로(REQUIRED 전파) 청크 실패 시 함께 롤백.
   *   - 정원 경계(0/총원)는 *ForSync 가 clamp 처리(예외 X)라 공유 트랜잭션을 rollback-only
   *     로 오염시키지 않는다. 진짜 오류(락 타임아웃 등)만 예외로 전파돼 트랜잭션 전체가
   *     깨끗이 롤백 → 호출자(Worker)가 단건 폴백/재시도로 재처리한다.
   *
   * ⚠️ Reservation.id 는 IDENTITY 라 Hibernate 신규 INSERT 배치는 동작하지 않는다(건별 실행).
   *    핵심 이득은 "청크당 트랜잭션/커밋 1회"로 commit 오버헤드 감소. 다만 슬롯 락을 청크
   *    동안 길게 쥐므로 hot 슬롯에서는 chunk-size 를 작게 두는 것이 안전하다.
   */
  @Transactional
  public void syncBatch(List<String> tokens) {
    List<String> distinct = new ArrayList<>(new LinkedHashSet<>(tokens));

    // 토큰별 Redis 데이터 로드 (없으면 drop)
    List<TokenItem> items = new ArrayList<>(distinct.size());
    for (String token : distinct) {
      ReservationTokenData data = reservationRedis.findByToken(token);
      if (data == null) {
        log.warn("Batch sync skipped — Redis hash missing. token={}", token);
        continue;
      }
      items.add(new TokenItem(token, data));
    }
    if (items.isEmpty()) return;

    // slotId 오름차순 — 슬롯 락 획득 순서 고정(교착 방지)
    items.sort(Comparator.comparing(it -> it.data().slotId()));

    Map<String, Reservation> existingByToken = reservationRepository
        .findByReservationTokenIn(distinct).stream()
        .collect(Collectors.toMap(Reservation::getReservationToken, Function.identity()));

    for (TokenItem it : items) {
      ReservationTokenData data = it.data();
      LocalDateTime visitedAt = toLdtNullable(data.visitedAt());
      LocalDateTime canceledAt = toLdtNullable(data.canceledAt());
      LocalDateTime noShowAt = toLdtNullable(data.noShowAt());
      LocalDateTime rejectedAt = toLdtNullable(data.rejectedAt());
      RejectionReason rejectionReason = data.rejectionReason() == null ? null
          : RejectionReason.valueOf(data.rejectionReason());

      Reservation existing = existingByToken.get(it.token());
      if (existing != null) {
        applyUpdate(existing, data, visitedAt, canceledAt, noShowAt, rejectedAt, rejectionReason);
      } else {
        applyInsert(it.token(), data, visitedAt, canceledAt, noShowAt, rejectedAt, rejectionReason);
      }
      reservationMetrics.persistSucceeded(data.createdAt());
    }
    log.debug("Batch synced reservation. tokens={}, distinct={}, processed={}",
        tokens.size(), distinct.size(), items.size());
  }

  /* slotId 정렬을 위한 (token, data) 묶음 */
  private record TokenItem(String token, ReservationTokenData data) {}

  /* 신규 INSERT */
  private void applyInsert(String token, ReservationTokenData data,
      LocalDateTime visitedAt, LocalDateTime canceledAt, LocalDateTime noShowAt,
      LocalDateTime rejectedAt, RejectionReason rejectionReason) {
    User user = userRepository.getReferenceById(data.userId());
    Restaurant restaurant = restaurantRepository.getReferenceById(data.restaurantId());
    Slot slot = slotRepository.findById(data.slotId())
        .orElseThrow(() -> new IllegalStateException(
            "Slot not found for sync. slotId=" + data.slotId()));

    Reservation entity = Reservation.register(token, user, restaurant, slot, data.headcount());

    ReservationStatus status = data.status();

    if (status == ReservationStatus.REJECTED) {
      entity.syncRejection(rejectionReason, rejectedAt);
    } else if (status == ReservationStatus.CONFIRMED) {
      entity.syncFromRedis(status, null, null, null);
    } else if (status != ReservationStatus.PENDING) {
      entity.syncFromRedis(status, visitedAt, canceledAt, noShowAt);
    }
    reservationRepository.save(entity);

    /* 슬롯 점유: PENDING/CONFIRMED/VISITED/NO_SHOW 차감. CANCELLED/REJECTED 는 미차감 (이미 복구됨) */
    if (status != ReservationStatus.CANCELLED && status != ReservationStatus.REJECTED) {
      slotService.decreaseForSync(slot.getId());
    }
  }

  /* UPDATE — 직전 상태와 신규 상태 비교해서 정원 멱등 조정 */
  private void applyUpdate(Reservation existing, ReservationTokenData data,
      LocalDateTime visitedAt, LocalDateTime canceledAt, LocalDateTime noShowAt,
      LocalDateTime rejectedAt, RejectionReason rejectionReason) {
    ReservationStatus prev = existing.getStatus();
    ReservationStatus next = data.status();

    if (next == ReservationStatus.REJECTED) {
      existing.syncRejection(rejectionReason, rejectedAt);
    } else {
      existing.syncFromRedis(next, visitedAt, canceledAt, noShowAt);
    }

    /* 활성(PENDING/CONFIRMED) → CANCELLED 또는 REJECTED 전이 시 정원 복구 */
    boolean wasActive = prev == ReservationStatus.CONFIRMED || prev == ReservationStatus.PENDING;
    boolean isFreed = next == ReservationStatus.CANCELLED || next == ReservationStatus.REJECTED;
    if (wasActive && isFreed) {
      slotService.increaseForSync(existing.getSlot().getId());
    }
  }

  /*
   * 슬롯 정원 차감/복구는 slotService.decreaseForSync()/increaseForSync() 로 처리한다.
   *
   * ⚠️ 예외를 catch 로 삼키지 않는다. 과거에는 decrease()/increase() 를 try/catch 로
   *    감쌌으나, 이 메서드들은 @Transactional(REQUIRED) 로 sync 트랜잭션에 합류하므로
   *    정원 경계에서 BusinessException(SLOT_FULL/SLOT_REMAIN_INVALID) 을 던지면 공유
   *    트랜잭션이 rollback-only 로 마킹된다. 예외를 삼켜도 플래그는 남아 상위 커밋이
   *    UnexpectedRollbackException 으로 실패 → 청크의 정상 토큰까지 DLQ 로 갔다.
   *    → *ForSync 변형이 경계에서 clamp(예외 X) 하므로 삼킬 필요가 없어졌고, 진짜
   *      일시적 오류(락 타임아웃/교착)는 그대로 전파돼 트랜잭션이 깨끗이 롤백되고
   *      Worker 가 단건 폴백/재시도로 처리한다.
   *
   * 동시성: decreaseForSync/increaseForSync 는 PESSIMISTIC_WRITE 조회로 슬롯 행 락을
   *    건 뒤 갱신해 lost update 를 막고, slot/slot_detail 캐시를 evict 한다(Redis 캐시라
   *    EKS 멀티 Pod 에서도 즉시 전파).
   */

  private static LocalDateTime toLdtNullable(Long millis) {
    if (millis == null) return null;
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), com.nowait.global.common.TimeZones.KST);
  }
}
