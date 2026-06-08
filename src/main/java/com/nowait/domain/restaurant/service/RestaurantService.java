package com.nowait.domain.restaurant.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.restaurant.dto.RestaurantDetailResponse;
import com.nowait.domain.restaurant.dto.RestaurantListResponse;
import com.nowait.domain.restaurant.dto.RestaurantRegisterRequest;
import com.nowait.domain.restaurant.dto.RestaurantUpdateRequest;
import com.nowait.domain.restaurant.entity.Restaurant;
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
	
	@Transactional
	public Long registerRestaurant(RestaurantRegisterRequest request, Long ownerId) {
		Restaurant restaurant = request.toEntity(ownerId);
		Restaurant savedRestaurant = restaurantRepository.save(restaurant);
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
