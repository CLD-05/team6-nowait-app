package com.nowait.domain.slot.service;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.dto.SlotCreateRequest;
import com.nowait.domain.slot.dto.SlotResponse;
import com.nowait.domain.slot.dto.SlotUpdateRequest;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotService {

    private final SlotRepository slotRepository;
    private final RestaurantRepository restaurantRepository;

    // 슬롯 목록 조회
    public SlotResponse getSlots(Long restaurantId, LocalDate date) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);  // 수정
        }
        List<Slot> slots = slotRepository
            .findByRestaurantIdAndSlotDate(restaurantId, date);
        return SlotResponse.of(restaurantId, date, slots);
    }

    // 슬롯 생성 (점주 전용)
    @Transactional
    public SlotResponse.SlotInfo createSlot(Long restaurantId,
                                             SlotCreateRequest request) {
        Restaurant restaurant = restaurantRepository
            .findById(restaurantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.RESTAURANT_NOT_FOUND));  // 수정

        slotRepository.findByRestaurantIdAndSlotDateAndSlotTime(
            restaurantId,
            request.getSlotDate(),
            request.getSlotTime()
        ).ifPresent(s -> {
            throw new BusinessException(ErrorCode.DUPLICATE_SLOT);
        });

        Slot slot = Slot.builder()
            .restaurant(restaurant)
            .slotDate(request.getSlotDate())
            .slotTime(request.getSlotTime())
            .totalCount(request.getTotalCount())
            .minHeadcount(request.getMinHeadcount())
            .maxHeadcount(request.getMaxHeadcount())
            .build();

        return SlotResponse.SlotInfo.from(slotRepository.save(slot));
    }

    // 슬롯 수정 (점주 전용)
    @Transactional
    public SlotResponse.SlotInfo updateSlot(Long slotId,
                                             SlotUpdateRequest request) {
        Slot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SLOT_NOT_FOUND));

        int diff = request.getTotalCount() - slot.getTotalCount();
        slot.updateTotalCount(request.getTotalCount(), diff);
        
        slot.updateHeadcountRestrictions(request.getMinHeadcount(), request.getMaxHeadcount());
        return SlotResponse.SlotInfo.from(slot);
    }

    // 잔여 수 감소 (예약 생성 시 호출)
    @Transactional
    public void decrease(Long slotId) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SLOT_NOT_FOUND));
        slot.decrease();
    }

    // 잔여 수 증가 (예약 취소 시 호출)
    @Transactional
    public void increase(Long slotId) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SLOT_NOT_FOUND));
        slot.increase();
    }
}
