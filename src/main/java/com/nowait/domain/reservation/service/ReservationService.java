package com.nowait.domain.reservation.service;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.reservation.dto.ReservationCreateRequest;
import com.nowait.domain.reservation.dto.ReservationResponse;
import com.nowait.domain.reservation.entity.Reservation;
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
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantHourRepository restaurantHourRepository;
    private final SlotRepository slotRepository;

    /**
     * 예약 생성
     * 1. 유저/식당/슬롯 존재 확인
     * 2. 슬롯 잔여 수 확인
     * 3. 동일 슬롯 중복 예약 확인
     * 4. 예약 생성 + 슬롯 잔여 수 차감
     */
    @Transactional
    public ReservationResponse createReservation(Long userId, ReservationCreateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        Slot slot = slotRepository.findById(request.slotId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        
        if (restaurant.getStatus() != RestaurantStatus.OPEN) {
        	throw new BusinessException(ErrorCode.RESTAURANT_NOT_OPEN);
        }
        
        if ("N".equals(restaurant.getReservationAvailable())) {
        	throw new BusinessException(ErrorCode.RESTAURANT_NOT_OPEN);
        }
        
        // =================================================================
        // 🌟 요일별 휴무일 및 영업시간 체킹
        // =================================================================
        
        // A. 자바 표준 요일 이름(ex: "MONDAY")을 구한 뒤, 앞 3글자만 잘라 대문자("MON")로 만듭니다.
        String dayName3Letters = slot.getSlotDate().getDayOfWeek().name().substring(0, 3);
        
        DayOfWeek customDayOfWeek = DayOfWeek.valueOf(dayName3Letters);

        // B. 해당 식당의 해당 요일 영업 정보 조회
        RestaurantHour hourInfo = restaurantHourRepository
    		.findByRestaurantIdAndDayOfWeek(restaurant.getId(), customDayOfWeek)
            .orElseThrow(() -> new BusinessException(ErrorCode.OPERATING_INFO_NOT_FOUND));

        // C. 정기 휴무일인지 검증
        if ("Y".equals(hourInfo.getIsRegularHoliday())) {
            throw new BusinessException(ErrorCode.RESTAURANT_CLOSED_DAY);
        }

        // D. 슬롯 시간이 실제 영업시간 내에 포함되는지 검증
        LocalTime slotTime = slot.getSlotTime();
        if (slotTime.isBefore(hourInfo.getOpenTime()) || slotTime.isAfter(hourInfo.getCloseTime())) {
            throw new BusinessException(ErrorCode.NOT_OPERATING_TIME);
        }
        
        // 해당 타임 슬롯의 최소/최대 인원수 검증
        int headcount = request.headcount(); // Record 문법 구조에 맞춰 request.headcount()로 호출!
        if (headcount < slot.getMinHeadcount()) {
            throw new BusinessException(ErrorCode.INVALID_MIN_HEADCOUNT);
        }
        if (slot.getMaxHeadcount() != null && headcount > slot.getMaxHeadcount()) {
            throw new BusinessException(ErrorCode.INVALID_MAX_HEADCOUNT);
        }

        // 슬롯 마감 여부 확인
        if (slot.getRemainCount() <= 0) {
            throw new BusinessException(ErrorCode.SLOT_FULL);
        }

        // 동일 슬롯 중복 예약 확인 (CONFIRMED 상태인 것만)
        boolean alreadyReserved = reservationRepository.existsByUserIdAndSlotIdAndStatus(
            userId, slot.getId(), ReservationStatus.CONFIRMED
        );
        if (alreadyReserved) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESERVATION);
        }

        // 슬롯 잔여 수 차감
        slot.decrease();

        // 예약 생성
        Reservation reservation = Reservation.builder()
            .user(user)
            .restaurant(restaurant)
            .slot(slot)
            .headcount(request.headcount())
            .build();

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    /**
     * 내 예약 목록 조회
     */
    public List<ReservationResponse> getMyReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * 예약 상세 조회
     * - 본인 예약만 조회 가능
     */
    public ReservationResponse getReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        return ReservationResponse.from(reservation);
    }

    /**
     * 예약 취소
     * - 본인 예약만 취소 가능
     * - CONFIRMED 상태만 취소 가능
     * - 취소 시 슬롯 잔여 수 복구
     */
    @Transactional
    public ReservationResponse cancelReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELLED_RESERVATION);
        }

        if (!reservation.isCancellable()) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_RESERVATION);
        }

        // 슬롯 잔여 수 복구
        reservation.getSlot().increase();

        reservation.cancel();

        return ReservationResponse.from(reservation);
    }

    /**
     * [점주 전용] 방문 완료 처리
     */
    @Transactional
    public ReservationResponse markVisited(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_RESERVATION);
        }

        reservation.markVisited();

        return ReservationResponse.from(reservation);
    }

    /**
     * [점주 전용] 노쇼 처리
     */
    @Transactional
    public ReservationResponse markNoShow(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_RESERVATION);
        }

        reservation.markNoShow();

        return ReservationResponse.from(reservation);
    }
}