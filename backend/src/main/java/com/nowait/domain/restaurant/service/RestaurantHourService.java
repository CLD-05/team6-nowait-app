package com.nowait.domain.restaurant.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.restaurant.dto.RestaurantHourRequest;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantHourService {
	
	private final RestaurantRepository restaurantRepository;
	private final RestaurantHourRepository restaurantHourRepository;
	
	/**
     * 점주 전용: 요일별 영업시간 설정 (등록 및 수정 통합)
     */
    @Transactional
    @CacheEvict(value = "restaurant_hours", key = "#restaurantId", cacheManager = "cacheManager")
    public void saveOrUpdateRestaurantHours(Long restaurantId, List<RestaurantHourRequest> requests) {
    	
    	log.info("💥 [Cache Evict] 영업시간 설정이 변경되어 Redis 캐시를 삭제합니다. 식당 ID: {}", restaurantId);
    	
        // 1. 식당 존재 여부 확인
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        // 2. 점주가 보낸 요일별 데이터 리스트를 하나씩 처리
        for (RestaurantHourRequest request : requests) {
            
            // 3. 이미 DB에 해당 식당의 '그 요일' 데이터가 등록되어 있는지 조회
            restaurantHourRepository.findByRestaurantIdAndDayOfWeek(restaurantId, request.getDayOfWeek())
                .ifPresentOrElse(
                    // A. 이미 존재하면 ➡️ 데이터 수정(Update)
                    existingHour -> existingHour.updateHour(
                        request.getOpenTime(),
                        request.getCloseTime(),
                        request.getIsRegularHoliday()
                    ),
                    // B. 없으면 ➡️ 새로 생성해서 저장(Insert)
                    () -> {
                        RestaurantHour newHour = RestaurantHour.builder()
                            .restaurant(restaurant)
                            .dayOfWeek(request.getDayOfWeek())
                            .openTime(request.getOpenTime())
                            .closeTime(request.getCloseTime())
                            .isRegularHoliday(request.getIsRegularHoliday())
                            .build();
                        restaurantHourRepository.save(newHour);
                    }
                );
        }
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "restaurant_hours", key = "#restaurantId", cacheManager = "cacheManager")
    public List<RestaurantHourRequest> getRestaurantHours(Long restaurantId) {
    	
    	log.info("🔍 [MySQL 조회] 영업시간 정책을 DB에서 읽어옵니다. ID: {}", restaurantId);
    	
    	if (!restaurantRepository.existsById(restaurantId)) {
    		throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
    	}
    	
    	List<RestaurantHour> hours = restaurantHourRepository.findByRestaurantId(restaurantId);
    	
    	return hours.stream()
    			.map(hour -> {
    				RestaurantHourRequest dto = new RestaurantHourRequest();
    				
    				return new RestaurantHourRequest(
    						hour.getDayOfWeek(),
    						hour.getOpenTime(),
    						hour.getCloseTime(),
    						hour.getIsRegularHoliday()
    						);
    			})
    			.toList();
    }

}
