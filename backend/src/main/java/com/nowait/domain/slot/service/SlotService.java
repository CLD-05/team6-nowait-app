package com.nowait.domain.slot.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.dto.SlotCreateRequest;
import com.nowait.domain.slot.dto.SlotDateTime;
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
    private final CacheManager cacheManager;

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
     *
     * Slot 엔티티를 그대로 반환/캐싱하면 안 된다: restaurant가 LAZY 연관관계라 캐시 PUT
     * 시점에 Hibernate 프록시(ByteBuddyInterceptor)까지 직렬화하려다 SerializationException이
     * 난다 (Jackson은 프록시 내부 필드를 직렬화할 수 없음). 호출부가 필요한 값(날짜/시간)만
     * 담은 SlotDateTime DTO로 변환해 캐싱한다.
     */
    @Cacheable(value = "slot_detail", key = "#slotId", cacheManager = "cacheManager", unless = "#result == null")
    public SlotDateTime getSlotById(Long slotId) {
        log.info("🔍 [MySQL 관통] 캐시에 단건 정보가 없어서 DB에서 읽어옵니다. 슬롯 ID: {}", slotId);
        return slotRepository.findById(slotId)
            .map(slot -> new SlotDateTime(slot.getId(), slot.getSlotDate(), slot.getSlotTime()))
            .orElse(null);
    }

    /**
     * ➕ 슬롯 생성 (점주 전용)
     * - 슬롯이 새로 생기면 해당 식당의 '그 날짜' 캐시를 폭파합니다.
     */
    @Transactional
    public SlotResponse.SlotInfo createSlot(Long restaurantId, SlotCreateRequest request) {
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

        Slot savedSlot = slotRepository.save(slot);
        reservationRedisLuaExecutor.initSlotCount(savedSlot.getId(), savedSlot.getTotalCount());
        evictSlotCaches(savedSlot);
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
    public SlotResponse.SlotInfo updateSlot(Long slotId, SlotUpdateRequest request) {
        Slot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

        int diff = request.getTotalCount() - slot.getTotalCount();
        slot.updateTotalCount(request.getTotalCount(), diff);
        slot.updateHeadcountRestrictions(request.getMinHeadcount(), request.getMaxHeadcount());
        evictSlotCaches(slot);
        return SlotResponse.SlotInfo.from(slot);
    }

    /**
     * 🗑️ 슬롯 삭제 (점주 전용)
     */
    @Transactional
    public void deleteSlot(Long slotId) {
        Slot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        evictSlotCaches(slot);
        slotRepository.delete(slot);
    }

    // 잔여 수 감소 (예약 생성 시 호출)
    @Transactional
    public void decrease(Long slotId) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SLOT_NOT_FOUND));
        slot.decrease();
        evictSlotCaches(slot);
    }

    // 잔여 수 증가 (예약 취소 시 호출)
    @Transactional
    public void increase(Long slotId) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SLOT_NOT_FOUND));
        slot.increase();
        evictSlotCaches(slot);
    }

    /*
     * Worker sync 재조정 전용 차감/복구 — 경계값에서 예외를 던지지 않는다(clamp).
     *
     * decrease()/increase() 와 락(PESSIMISTIC_WRITE)·캐시 evict 는 동일하지만, 정원
     * 경계(0/총원)에서 BusinessException 을 던지지 않는다. Worker 핸들러의 sync 트랜잭션에
     * REQUIRED 로 합류하므로, 여기서 예외를 던지면 공유 트랜잭션이 rollback-only 로
     * 마킹돼 상위 커밋이 UnexpectedRollbackException 으로 실패한다(→ 정상 토큰까지 DLQ).
     * 재조정 경로는 Redis 를 정합성 소스로 삼아 경계에서 조용히 유지하는 것이 안전하다.
     * (슬롯 자체가 없는 경우는 진짜 데이터 오류이므로 그대로 예외를 전파한다.)
     */
    @Transactional
    public void decreaseForSync(Long slotId) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        slot.decreaseForSync();
        evictSlotCaches(slot);
    }

    @Transactional
    public void increaseForSync(Long slotId) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        slot.increaseForSync();
        evictSlotCaches(slot);
    }

    /*
     * remainCount가 바뀐 슬롯의 캐시(목록/단건)를 무효화한다.
     * cacheManager가 RedisCacheManager이므로 한 Pod에서 evict하면 Redis 자체에서 키가
     * 지워져 모든 API/Worker Pod에 즉시 반영된다 (로컬 메모리 캐시가 아니라 EKS 멀티 Pod에서도 안전).
     */
    private void evictSlotCaches(Slot slot) {
        Cache slotCache = cacheManager.getCache("slot");
        if (slotCache != null) {
            slotCache.evict(slot.getRestaurant().getId() + "_" + slot.getSlotDate());
        }
        Cache slotDetailCache = cacheManager.getCache("slot_detail");
        if (slotDetailCache != null) {
            slotDetailCache.evict(slot.getId());
        }
    }
}
