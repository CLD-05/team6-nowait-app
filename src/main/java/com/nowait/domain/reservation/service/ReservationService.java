package com.nowait.domain.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.domain.reservation.dto.ReservationCreateRequest;
import com.nowait.domain.reservation.dto.ReservationResponse;
import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.repository.ReservationRepository;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.type.DayOfWeek;
import com.nowait.domain.restaurant.type.RestaurantStatus;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * 예약 서비스 — Redis-first 아키텍처.
 *
 * 동작 원리:
 *   1. 생성/취소/방문/노쇼 모두 Redis Lua 로 원자 처리
 *   2. DB INSERT/UPDATE 는 Worker(@Profile("reservation-worker")) 가 비동기로 수행
 *   3. 본 서비스는 DB 를 절대 mutate 하지 않음 (slot.decrease/increase 호출 X)
 *
 * 식별자:
 *   - 모든 후속 액션은 reservationToken (UUID) 기반
 *   - reservationId(Long) 는 Worker 가 DB sync 한 후에만 존재
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantHourRepository restaurantHourRepository;
    private final SlotRepository slotRepository;
    private final RestaurantOwnerRepository restaurantOwnerRepository;
    private final ReservationRedisLuaExecutor reservationRedis;

    /* ================== 사용자 ================== */

    /*
     * 예약 생성 — POST /api/v1/reservations
     * 검증은 DB, 생성은 Redis Lua. DB INSERT 는 Worker 가 비동기 처리.
     */
    public ReservationResponse createReservation(Long userId, ReservationCreateRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        Slot slot = slotRepository.findById(request.slotId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

        if (restaurant.getStatus() != RestaurantStatus.OPEN) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_OPEN);
        }
        if ("N".equals(restaurant.getReservationAvailable())) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_AVAILABLE);
        }

        /* 요일별 휴무일 / 영업시간 검증 */
        String dayName3 = slot.getSlotDate().getDayOfWeek().name().substring(0, 3);
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(dayName3);

        RestaurantHour hourInfo = restaurantHourRepository
            .findByRestaurantIdAndDayOfWeek(restaurant.getId(), dayOfWeek)
            .orElseThrow(() -> new BusinessException(ErrorCode.OPERATING_INFO_NOT_FOUND));

        if ("Y".equals(hourInfo.getIsRegularHoliday())) {
            throw new BusinessException(ErrorCode.RESTAURANT_CLOSED_DAY);
        }

        LocalTime slotTime = slot.getSlotTime();
        if (slotTime.isBefore(hourInfo.getOpenTime()) || slotTime.isAfter(hourInfo.getCloseTime())) {
            throw new BusinessException(ErrorCode.NOT_OPERATING_TIME);
        }

        /* 인원수 검증 */
        int headcount = request.headcount();
        if (headcount < slot.getMinHeadcount()) {
            throw new BusinessException(ErrorCode.INVALID_MIN_HEADCOUNT);
        }
        if (slot.getMaxHeadcount() != null && headcount > slot.getMaxHeadcount()) {
            throw new BusinessException(ErrorCode.INVALID_MAX_HEADCOUNT);
        }

        /* 예약 시각을 epoch millis 로 (노쇼 스케줄러 score 용) */
        long reservationTimeMillis = toEpochMillis(slot.getSlotDate(), slot.getSlotTime());

        /* Redis Lua — 중복 차단 / 정원 검증 / 토큰 생성 / 큐 푸시까지 원자 처리 */
        ReservationRedisLuaExecutor.CreateResult result = reservationRedis.create(
            userId,
            restaurant.getId(),
            slot.getId(),
            headcount,
            slot.getTotalCount(),
            reservationTimeMillis
        );

        log.info("Reservation created. token={}, userId={}, slotId={}",
            result.token(), userId, slot.getId());

        /* Redis Hash 에서 응답 구성 — Worker 가 비동기로 DB 에 INSERT */
        ReservationTokenData data = reservationRedis.findByToken(result.token());
        if (data == null) {
            log.error("Reservation hash missing from Redis immediately after creation. token={}", result.token());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return buildFromRedis(result.token(), data);
    }

    /*
     * 내 예약 목록 — DB 조회 (Worker 가 sync 한 결과).
     * 방금 생성한 예약은 sync 지연 (~500ms) 동안 미노출 가능.
     */
    public List<ReservationResponse> getMyReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /*
     * 예약 상세 (본인) — Redis 우선, 없으면 DB fallback.
     */
    public ReservationResponse getReservation(Long userId, String token) {
        ReservationTokenData data = reservationRedis.findByToken(token);
        if (data != null) {
            if (!data.userId().equals(userId)) {
                throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
            }
            return buildFromRedis(token, data);
        }

        Reservation reservation = reservationRepository.findByReservationToken(token)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }
        return ReservationResponse.from(reservation);
    }

    /*
     * 예약 취소 (사용자) — PATCH /api/v1/reservations/{token}/cancel
     * DB 상태 업데이트는 Worker 가 비동기 처리.
     */
    public ReservationResponse cancelReservation(Long userId, String token) {
        ReservationTokenData data = findTokenDataOrThrow(token);
        reservationRedis.cancel(token, userId, data.slotId());
        log.info("Reservation cancelled by user. token={}, userId={}", token, userId);

        ReservationTokenData updated = reservationRedis.findByToken(token);
        return buildFromRedis(token, updated == null ? data : updated);
    }

    /* ================== 점주 ================== */

    /*
     * 방문 완료 처리 (점주) — PATCH /api/v1/owner/reservations/{token}/visit
     * DB 상태 업데이트는 Worker 가 비동기 처리.
     */
    public ReservationResponse markVisited(Long ownerUserId, String token) {
        ReservationTokenData data = findTokenDataOrThrow(token);
        verifyOwnership(data.restaurantId(), ownerUserId);

        reservationRedis.visit(token, data.userId(), data.slotId());
        log.info("Reservation visited. token={}", token);

        ReservationTokenData updated = reservationRedis.findByToken(token);
        return buildFromRedis(token, updated == null ? data : updated);
    }

    /*
     * 노쇼 처리 (점주) — PATCH /api/v1/owner/reservations/{token}/noshow
     * DB 상태 업데이트는 Worker 가 비동기 처리.
     */
    public ReservationResponse markNoShow(Long ownerUserId, String token) {
        ReservationTokenData data = findTokenDataOrThrow(token);
        verifyOwnership(data.restaurantId(), ownerUserId);

        reservationRedis.noShow(token, data.userId(), data.slotId());
        log.info("Reservation no-show. token={}", token);

        ReservationTokenData updated = reservationRedis.findByToken(token);
        return buildFromRedis(token, updated == null ? data : updated);
    }

    // ================== 내부 헬퍼 ==================

    private ReservationTokenData findTokenDataOrThrow(String token) {
        ReservationTokenData data = reservationRedis.findByToken(token);
        if (data == null) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return data;
    }

    private void verifyOwnership(Long restaurantId, Long loginUserId) {
        if (!restaurantOwnerRepository.existsByUserIdAndRestaurantId(loginUserId, restaurantId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
    }

    /* 응답용 — restaurant/slot 메타데이터를 DB 에서 조회해서 합쳐줌 */
    private ReservationResponse buildFromRedis(String token, ReservationTokenData data) {
        String restaurantName = restaurantRepository.findById(data.restaurantId())
            .map(Restaurant::getName).orElse("매장");
        Slot slot = slotRepository.findById(data.slotId()).orElse(null);
        LocalDate slotDate = slot == null ? null : slot.getSlotDate();
        LocalTime slotTime = slot == null ? null : slot.getSlotTime();
        return ReservationResponse.fromRedis(token, data, restaurantName, slotDate, slotTime);
    }

    private static long toEpochMillis(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    }
}
