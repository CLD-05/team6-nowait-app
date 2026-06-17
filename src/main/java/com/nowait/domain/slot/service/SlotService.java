package com.nowait.domain.slot.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.dto.SlotCreateRequest;
import com.nowait.domain.slot.dto.SlotResponse;
import com.nowait.domain.slot.dto.SlotUpdateRequest;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotService {

    private final SlotRepository slotRepository;
    private final RestaurantRepository restaurantRepository;
    private final RedisTemplate redisTemplate;
    private final ReservationRedisLuaExecutor reservationRedisLuaExecutor;

    // 슬롯 목록 조회
    /**
     * 📅 슬롯 목록 조회 (캐싱 적용)
     * - key 복합 설정: restaurantId와 date를 묶어서 캐시 장부를 만듭니다. (예: slot::3_2026-06-16)
     */
    @Cacheable(value = "slot", key = "#restaurantId + '_' + #date", cacheManager = "cacheManager")
    public SlotResponse getSlots(Long restaurantId, LocalDate date) {
    	log.info("🔍 [MySQL 조회] 타임 슬롯 목록을 DB에서 읽어옵니다. 식당 ID: {}, 날짜: {}", restaurantId, date);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);  // 수정
        }
        List<Slot> slots = slotRepository
            .findByRestaurantIdAndSlotDate(restaurantId, date);
        return SlotResponse.of(restaurantId, date, slots);
    }
    
    /**
     * 🎯 [추가] 슬롯 단건 상세 조회 (캐싱 적용)
     * - 예약 응답을 조립할 때, '슬롯 ID' 딱 하나만 가지고도 
     * - MySQL을 찌르지 않고 Redis에서 0초 만에 꺼내오기 위한 단건 전용 자판기입니다.
     */
    @Cacheable(value = "slot_detail", key = "#slotId", cacheManager = "cacheManager")
    public Slot getSlotById(Long slotId) {
        log.info("🔍 [MySQL 관통] 캐시에 단건 정보가 없어서 DB에서 읽어옵니다. 슬롯 ID: {}", slotId);
        return slotRepository.findById(slotId).orElse(null);
    }

    /**
     * ➕ 슬롯 생성 (점주 전용)
     * - 슬롯이 새로 생기면 해당 식당의 '그 날짜' 캐시를 폭파합니다.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "slot", allEntries = true, cacheManager = "cacheManager"),
            @CacheEvict(value = "slot_detail", allEntries = true, cacheManager = "cacheManager") // 🎯 팀원 최신 코드 유지
        })
    public SlotResponse.SlotInfo createSlot(Long restaurantId, SlotCreateRequest request) {
        log.info("💥 [Cache Evict] 새 슬롯 생성으로 인해 캐시를 삭제합니다. 날짜: {}", request.getSlotDate());
        
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        
        slotRepository.findByRestaurantIdAndSlotDateAndSlotTime(
            restaurantId, request.getSlotDate(), request.getSlotTime()
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
        
        // 1. 깨끗하게 DB에 한 번만 저장
        Slot savedSlot = slotRepository.save(slot);
        
        // 2. ★ [구조 고도화] 레디스 실행기를 깨워서 깔끔하게 7일 TTL 초기화!
        reservationRedisLuaExecutor.initSlotCount(savedSlot.getId(), savedSlot.getTotalCount());

        // 3. 중복 저장 걷어내고 리턴
        return SlotResponse.SlotInfo.from(savedSlot);
    }

    /**
     * ✏️ 슬롯 수정 (점주 전용)
     * - 수정할 때는 매개변수에 restaurantId가 없고 slotId만 있습니다.
     * - 이 경우, DB에서 슬롯을 꺼낸 뒤 그 슬롯 안에 든 식당ID와 날짜를 조합해 캐시를 폭파해야 하므로,
     * - 어노테이션 하나로 제어하기보다, 메서드 내부나 엔티티 정보를 이용하는게 명확하지만 
     * - 점주가 슬롯 설정을 바꿀 때 전체 캐시를 날려버리는 가장 안전하고 속 편한 방식을 씁니다. (allEntries = true)
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "slot", allEntries = true, cacheManager = "cacheManager"),
            @CacheEvict(value = "slot_detail", allEntries = true, cacheManager = "cacheManager") // 🎯 추가!
        })
    public SlotResponse.SlotInfo updateSlot(Long slotId,
                                             SlotUpdateRequest request) {
    	log.info("💥 [Cache Evict] 슬롯 수정으로 안전을 위해 슬롯 캐시 장부를 리셋합니다.");
        Slot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SLOT_NOT_FOUND));

        int diff = request.getTotalCount() - slot.getTotalCount();
        slot.updateTotalCount(request.getTotalCount(), diff);
        
        slot.updateHeadcountRestrictions(request.getMinHeadcount(), request.getMaxHeadcount());
        return SlotResponse.SlotInfo.from(slot);
    }

    /**
     * 🗑️ 슬롯 삭제 (점주 전용)
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "slot", allEntries = true, cacheManager = "cacheManager"),
            @CacheEvict(value = "slot_detail", allEntries = true, cacheManager = "cacheManager") // 🎯 추가!
        })
    public void deleteSlot(Long slotId) {
    	log.info("💥 [Cache Evict] 슬롯 삭제로 슬롯 캐시 장부를 리셋합니다.");
        Slot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        slotRepository.delete(slot);
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
