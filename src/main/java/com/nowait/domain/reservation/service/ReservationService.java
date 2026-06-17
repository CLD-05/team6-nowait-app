package com.nowait.domain.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.reservation.dto.RejectReservationRequest;
import com.nowait.domain.reservation.dto.ReservationCreateRequest;
import com.nowait.domain.reservation.dto.ReservationResponse;
import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.repository.ReservationRepository;
import com.nowait.domain.reservation.type.ReservationStatus;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.type.DayOfWeek;
import com.nowait.domain.restaurant.type.RestaurantStatus;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.domain.slot.service.SlotService;
import com.nowait.domain.user.repository.UserRepository;
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
    private final SlotService slotService;
    private final UserRepository userRepository;
    private final ReservationRedisLuaExecutor reservationRedis;
    private final StringRedisTemplate redisTemplate;

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
        if (headcount > slot.getMaxHeadcount()) {
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
     * 내 예약 목록 — Redis 사용자별 토큰 목록 기준, DB 상태 우선 반영.
     *
     * 1) Redis의 사용자별 토큰 목록(ZSET)에서 전체 토큰을 가져옴
     *    → Worker 실패(dead-letter)가 있어도 토큰은 여기에 남아있음
     * 2) DB에 sync된 예약은 DB 정보 우선, Redis 최신 상태 보정
     * 3) DB에 없는 예약(PENDING_SYNC 중) → Redis Hash에서 직접 구성
     */
    public List<ReservationResponse> getMyReservations(Long userId) {
        // DB 예약 목록 — Redis Hash 최신 상태 우선 반영
        List<ReservationResponse> dbResults = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(reservation -> {
                ReservationTokenData redisData = reservationRedis.findByToken(reservation.getReservationToken());
                if (redisData != null && userId.equals(redisData.userId())) {
                    return buildFromRedis(reservation.getReservationToken(), redisData);
                }
                return ReservationResponse.from(reservation);
            })
            .toList();

        Set<String> dbTokens = dbResults.stream()
            .map(ReservationResponse::reservationToken)
            .collect(Collectors.toSet());

        // 사용자별 토큰 목록에서 DB에 없는 예약 보충 (PENDING_SYNC, PROCESSING, dead-letter 모두 포함)
        List<ReservationResponse> redisOnly = reservationRedis.listUserTokens(userId)
            .stream()
            .filter(token -> !dbTokens.contains(token))
            .<ReservationResponse>flatMap(token -> {
                ReservationTokenData data = reservationRedis.findByToken(token);
                if (data == null || !userId.equals(data.userId())) return Stream.empty();
                return Stream.of(buildFromRedis(token, data));
            })
            .toList();

        List<ReservationResponse> merged = new ArrayList<>();
        merged.addAll(redisOnly);
        merged.addAll(dbResults);
        merged.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return merged;
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
     * Redis 우선 취소; Redis TTL 만료 시 DB 직접 취소(fallback).
     */
    @Transactional
    public ReservationResponse cancelReservation(Long userId, String token) {
        ReservationTokenData data = reservationRedis.findByToken(token);
        if (data != null) {
            reservationRedis.cancel(token, userId, data.slotId());
            log.info("Reservation cancelled via Redis. token={}, userId={}", token, userId);
            ReservationTokenData updated = reservationRedis.findByToken(token);
            return buildFromRedis(token, updated == null ? data : updated);
        }

        // Redis TTL 만료 — DB 직접 취소 (슬롯 카운트 조정 없이 상태만 변경)
        Reservation reservation = reservationRepository.findByReservationToken(token)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                && reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELLED_RESERVATION);
        }
        reservation.syncFromRedis(
            ReservationStatus.CANCELLED,
            reservation.getVisitedAt(),
            LocalDateTime.now(),
            reservation.getNoShowAt()
        );
        
        // ★ [핵심 추가] DB의 슬롯 카운트도 반드시 복구(Increase)해줍니다!
        // reservation 엔티티가 가진 slot의 ID를 이용해 수량을 1 늘려줍니다.
        if (reservation.getSlot() != null) {
            slotService.increase(reservation.getSlot().getId());
            log.info("Slot count restored via DB fallback. slotId={}", reservation.getSlot().getId());
        }
        
        log.info("Reservation cancelled via DB fallback. token={}, userId={}", token, userId);
        return ReservationResponse.from(reservation);
    }

    /* ================== 점주 ================== */

    /*
     * 식당 예약 목록 (점주) — GET /api/v1/owner/restaurants/{restaurantId}/reservations
     * Redis 매장별 토큰 목록 기준 조회 → Worker 실패 여부와 무관하게 항상 표시됨.
     */
    public List<ReservationResponse> getRestaurantReservations(Long ownerUserId, Long restaurantId) {
        verifyOwnership(restaurantId, ownerUserId);

        List<ReservationResponse> dbResults = reservationRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .stream()
            .map(reservation -> {
                ReservationTokenData redisData = reservationRedis.findByToken(reservation.getReservationToken());
                if (redisData != null) return buildFromRedis(reservation.getReservationToken(), redisData);
                return ReservationResponse.from(reservation);
            })
            .toList();

        Set<String> dbTokens = dbResults.stream()
            .map(ReservationResponse::reservationToken)
            .collect(Collectors.toSet());

        // 매장별 토큰 목록에서 DB에 없는 예약 보충 (PENDING_SYNC, dead-letter 모두 포함)
        List<ReservationResponse> redisOnly = reservationRedis.listRestaurantTokens(restaurantId)
            .stream()
            .filter(token -> !dbTokens.contains(token))
            .<ReservationResponse>flatMap(token -> {
                ReservationTokenData data = reservationRedis.findByToken(token);
                if (data == null || !restaurantId.equals(data.restaurantId())) return Stream.empty();
                return Stream.of(buildFromRedis(token, data));
            })
            .toList();

        List<ReservationResponse> merged = new ArrayList<>();
        merged.addAll(redisOnly);
        merged.addAll(dbResults);
        merged.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return merged;
    }

    /*
     * 예약 승인 (점주) — PATCH /api/v1/owner/reservations/{token}/approve
     * PENDING → CONFIRMED
     */
    @Transactional
    public ReservationResponse approveReservation(Long ownerUserId, String token) {
        ReservationTokenData data = findTokenDataOrThrow(token);
        verifyOwnership(data.restaurantId(), ownerUserId);

        if (data.status() != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        reservationRedis.approve(token);
        log.info("Reservation approved. token={}", token);

        ReservationTokenData updated = reservationRedis.findByToken(token);
        return buildFromRedis(token, updated == null ? data : updated);
    }

    /*
     * 예약 거부 (점주) — PATCH /api/v1/owner/reservations/{token}/reject
     * PENDING → REJECTED
     */
    @Transactional
    public ReservationResponse rejectReservation(Long ownerUserId, String token, RejectReservationRequest request) {
        ReservationTokenData data = findTokenDataOrThrow(token);
        // [케이스 1] Redis에 데이터가 생존해 있을 때 -> 기존 고속 처리 경로
        if (data != null) {
            verifyOwnership(data.restaurantId(), ownerUserId);
            if (data.status() != ReservationStatus.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
            reservationRedis.reject(token, data.userId(), data.slotId(), request.reason().name());
            log.info("Reservation rejected via Redis. token={}, reason={}", token, request.reason());

            ReservationTokenData updated = reservationRedis.findByToken(token);
            return buildFromRedis(token, updated == null ? data : updated);
        }
        
        // [케이스 2] ★ 새 지평 ★ Redis TTL 만료 시 — DB 직접 거부 (Fallback)
        Reservation reservation = reservationRepository.findByReservationToken(token)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        
        verifyOwnership(reservation.getRestaurant().getId(), ownerUserId);
        
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        
        // DB의 예약 상태 직접 업데이트
        reservation.syncFromRedis(
            ReservationStatus.REJECTED,
            reservation.getVisitedAt(),
            LocalDateTime.now(), // 거부 일시 기록
            reservation.getNoShowAt()
        );
        
        // 거부되었으므로 DB 내 매장의 해당 타임 슬롯 카운트 원상 복구!
        if (reservation.getSlot() != null) {
            slotService.increase(reservation.getSlot().getId());
            log.info("Slot count restored via DB reject fallback. slotId={}", reservation.getSlot().getId());
        }

        log.info("Reservation rejected via DB fallback. token={}", token);
        return ReservationResponse.from(reservation);
    }

    /*
     * 방문 완료 처리 (점주) — PATCH /api/v1/owner/reservations/{token}/visit
     * DB 상태 업데이트는 Worker 가 비동기 처리.
     */
    @Transactional
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
    @Transactional
    public ReservationResponse markNoShow(Long ownerUserId, String token) {
        ReservationTokenData data = findTokenDataOrThrow(token);
        verifyOwnership(data.restaurantId(), ownerUserId);

        reservationRedis.noShow(token, data.userId(), data.slotId());
        log.info("Reservation no-show. token={}", token);

        ReservationTokenData updated = reservationRedis.findByToken(token);
        return buildFromRedis(token, updated == null ? data : updated);
    }

    /* ================== 사용자 — 내역 관리 ================== */

    /*
     * 예약 내역 단건 삭제 — DELETE /api/v1/reservations/{token}
     * CANCELLED / NO_SHOW 상태만 삭제 허용.
     * DB 행 삭제 + 남아있는 Redis Hash 정리.
     */
    @Transactional
    public void deleteReservation(Long userId, String token) {
        boolean found = false;

        // 1) Redis 확인 — Worker sync 전이라 DB 상태가 CONFIRMED일 수 있으므로 Redis 우선
        ReservationTokenData redisData = reservationRedis.findByToken(token);
        if (redisData != null) {
            if (!userId.equals(redisData.userId())) {
                throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
            }
            if (redisData.status() == ReservationStatus.CONFIRMED) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
            redisTemplate.delete(ReservationRedisKeys.token(token));
            found = true;
        }

        // 2) DB 확인 — sync 완료된 경우 또는 Redis TTL 만료 시
        Optional<Reservation> dbReservation = reservationRepository.findByTokenAndUserId(token, userId);
        if (dbReservation.isPresent()) {
            Reservation reservation = dbReservation.get();
            // Redis가 없는 경우에만 DB 상태 검증 (Redis가 있으면 이미 위에서 검증)
            if (redisData == null && reservation.getStatus() == ReservationStatus.CONFIRMED) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
            reservationRepository.delete(reservation);
            found = true;
        }

        if (!found) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        log.info("Deleted reservation token={}, userId={}", token, userId);
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
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!loginUserId.equals(restaurant.getOwnerId())) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
    }

    /* 응답용 — restaurant/slot/user 메타데이터를 DB 에서 조회해서 합쳐줌 */
    private ReservationResponse buildFromRedis(String token, ReservationTokenData data) {
        String restaurantName = restaurantRepository.findById(data.restaurantId())
            .map(Restaurant::getName).orElse("매장");
        String userName = userRepository.findById(data.userId())
            .map(u -> u.getName()).orElse("고객");
        Slot slot = slotRepository.findById(data.slotId()).orElse(null);
        LocalDate slotDate = slot == null ? null : slot.getSlotDate();
        LocalTime slotTime = slot == null ? null : slot.getSlotTime();
        return ReservationResponse.fromRedis(token, data, restaurantName, userName, slotDate, slotTime);
    }

    private static long toEpochMillis(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    }
}
