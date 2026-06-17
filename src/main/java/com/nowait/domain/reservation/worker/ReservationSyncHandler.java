package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.repository.ReservationRepository;
import com.nowait.domain.reservation.type.RejectionReason;
import com.nowait.domain.reservation.type.ReservationStatus;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
  private final ReservationRedisLuaExecutor reservationRedis;

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

    log.debug("Synced. token={} status={}", token, data.status());
    return true;
  }

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
      decreaseSlotSafely(slot);
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
      increaseSlotSafely(existing.getSlot());
    }
  }

  /* 슬롯 정원 차감 — 0 이하 방어 (Redis 가 이미 검증했지만 DB 안전망) */
  private void decreaseSlotSafely(Slot slot) {
    try {
      slot.decrease();
    } catch (Exception e) {
      log.warn("Failed to decrease slot. slotId={} reason={}", slot.getId(), e.getMessage());
    }
  }

  /* 슬롯 정원 복구 — totalCount 초과 방어 */
  private void increaseSlotSafely(Slot slot) {
    try {
      slot.increase();
    } catch (Exception e) {
      log.warn("Failed to increase slot. slotId={} reason={}", slot.getId(), e.getMessage());
    }
  }

  private static LocalDateTime toLdtNullable(Long millis) {
    if (millis == null) return null;
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), com.nowait.global.common.TimeZones.KST);
  }
}
