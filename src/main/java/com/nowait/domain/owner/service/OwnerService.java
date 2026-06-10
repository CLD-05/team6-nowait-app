package com.nowait.domain.owner.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.owner.dto.OwnerRegisterRequest;
import com.nowait.domain.owner.dto.OwnerResponse;
import com.nowait.domain.owner.entity.RestaurantOwner;
import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerService {
	
	private final RestaurantRepository restaurantRepository;
	private final RestaurantOwnerRepository restaurantOwnerRepository;
	
	@Transactional
	public OwnerResponse registerOwner(OwnerRegisterRequest request) {
		if (restaurantOwnerRepository.existsByUserId(request.getUserId())) {
			throw new BusinessException(ErrorCode.OWNER_ALREADY_EXISTS);
		}
		
		if (restaurantOwnerRepository.existsByRestaurantId(request.getRestaurantId())) {
			throw new BusinessException(ErrorCode.RESTAURANT_ALREADY_HAS_OWNER);
		}
	
		RestaurantOwner restaurantOwner = RestaurantOwner.builder()
				.userId(request.getUserId())
				.restaurantId(request.getRestaurantId())
				.build();
		
		RestaurantOwner savedOwner = restaurantOwnerRepository.save(restaurantOwner);
		
		return OwnerResponse.from(savedOwner);
	}
	
	public OwnerResponse getOwnerRestaurant(Long userId) {
		RestaurantOwner restaurantOwner = restaurantOwnerRepository.findByUserId(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER));
		
		return OwnerResponse.from(restaurantOwner);
	}
	
	@Transactional
	public void deleteOwner(Long ownerId) {
		// 1. 🔍 현재 로그인한 사용자가 사장님(RestaurantOwner)이 맞는지 검증하고 찾아옵니다.
	    RestaurantOwner restaurantOwner = restaurantOwnerRepository.findByUserId(ownerId)
	            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER));
	    
	    // 2. 🌟 [소프트 딜리트] DB에서 행을 지우지 않고, 방금 엔티티에 만드신 withdrawOwner()를 호출해 'Y'로 마킹합니다!
	    restaurantOwner.withdrawOwner();
	    
	    // 3. ⛓️ [연쇄 반응] 이 사장님이 운영하던 '삭제되지 않은 정상 식당들'을 전수 조사합니다.
	    List<Restaurant> myRestaurants = restaurantRepository.findByOwnerIdAndIsDeleted(ownerId, "N");
	    
	    // 4. 🚫 조사된 사장님의 식당들을 전부 연쇄 폐업(is_deleted = 'Y') 처리합니다.
	    for (Restaurant restaurant : myRestaurants) {
	        restaurant.deleteRestaurant(); // 식당도 함께 Soft Delete + 영구 폐업 상태 전환!
	    }
	}
}
