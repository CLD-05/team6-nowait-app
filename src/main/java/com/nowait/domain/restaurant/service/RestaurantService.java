package com.nowait.domain.restaurant.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.restaurant.dto.RestaurantDetailResponse;
import com.nowait.domain.restaurant.dto.RestaurantHourRequest;
import com.nowait.domain.restaurant.dto.RestaurantListResponse;
import com.nowait.domain.restaurant.dto.RestaurantRegisterRequest;
import com.nowait.domain.restaurant.dto.RestaurantUpdateRequest;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {
	
	private final RestaurantRepository restaurantRepository;
	private final RestaurantHourRepository restaurantHourRepository;
	
	@Transactional
    public Long registerRestaurant(RestaurantRegisterRequest request, Long ownerId) {
        // 1. 기존 로직: 식당 정보를 먼저 저장합니다.
        Restaurant restaurant = request.toEntity(ownerId);
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        // 식당이 등록될 때 월~일요일 기본 영업시간 7줄을 자동으로 깔아줍니다!
        for (RestaurantHourRequest hourReq : request.getRestaurantHours()) {
        	
        	// 💡 정기 휴무('Y')인 요일은 시간이 누락될 수 있으므로, 안전하게 00:00(MIDNIGHT)으로 메워주는 방어 코드
            boolean isHoliday = "Y".equals(hourReq.getIsRegularHoliday());
            java.time.LocalTime finalOpenTime = isHoliday ? java.time.LocalTime.MIDNIGHT : hourReq.getOpenTime();
            java.time.LocalTime finalCloseTime = isHoliday ? java.time.LocalTime.MIDNIGHT : hourReq.getCloseTime();
            
         // 2. 요일별 테이블(RestaurantHour)에 연관 관계를 맺어 챡챡 쌓아줍니다.
            RestaurantHour restaurantHour = RestaurantHour.builder()
                .restaurant(savedRestaurant)                    // 위에서 방금 영속화된 식당 엔티티
                .dayOfWeek(hourReq.getDayOfWeek())              // 프론트가 보낸 요일 (MON, TUE 등)
                .openTime(finalOpenTime)                        // 가공 완료된 오픈 시간
                .closeTime(finalCloseTime)                      // 가공 완료된 마감 시간
                .isRegularHoliday(hourReq.getIsRegularHoliday())// 정기 휴무 여부 ('Y' 또는 'N')
                .build();
            
            restaurantHourRepository.save(restaurantHour); // 요일별 테이블에 한 줄씩 인서트!
        }

        return savedRestaurant.getId();
    }
	
	public List<RestaurantListResponse> getAllRestaurants() {
		return restaurantRepository.findAll().stream()
				.map(RestaurantListResponse::from)
				.collect(Collectors.toList());
	}
	
	public List<RestaurantListResponse> searchRestaurantsByName(String keyword) {
	    return restaurantRepository.findByNameContaining(keyword).stream()
	            .map(RestaurantListResponse::from)
	            .collect(Collectors.toList());
	}
	
	public List<RestaurantListResponse> getRestaurantsByCategory(RestaurantCategory category) {
		return restaurantRepository.findByCategory(category).stream()
				.map(RestaurantListResponse::from)
				.collect(Collectors.toList());
	}
	
	public RestaurantDetailResponse getRestaurantDetail(Long restaurantId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		return RestaurantDetailResponse.from(restaurant);
	}
	
	@Transactional
	public void updateRestaurant(Long restaurantId, RestaurantUpdateRequest request, Long owenrId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		
		if (!restaurant.getOwnerId().equals(owenrId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}
	}
	
	@Transactional
	public void deleteRestaurant(Long restaurantId, Long ownerId) {
		
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		
		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}
		// [주의] 지금은 아직 MySQL에 칼럼을 안 만들었으니, 
        // 나중에 팀원들과 이야기해서 엔티티에 필드 추가하면 아래 주석을 풀어줄 것입니다!
        // restaurant.softDelete();
	}
	
	
	@Transactional
    public void updateRestaurantImage(Long restaurantId, Long ownerId, String imageUrl) {
        // 식당이 존재하는지 확인
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }

        // 엔티티의 메서드를 호출해서 새 이미지 URL로 갈아끼웁니다 (더티 체킹으로 자동 저장)
        restaurant.updateImage(imageUrl);
    }
	
	@Transactional
    public void deleteRestaurantImage(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }

        // 이미지 주소를 완전히 비워줍니다 (null)
        restaurant.updateImage(null);
    }
}
