package com.nowait.domain.owner.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.owner.dto.OwnerRegisterRequest;
import com.nowait.domain.owner.dto.OwnerResponse;
import com.nowait.domain.owner.entity.RestaurantOwner;
import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerService {
	
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
}
