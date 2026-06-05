package com.nowait.domain.restaurant.service;

<<<<<<< HEAD
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
			throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
		}
	}
=======
public class RestaurantService {

>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
}
