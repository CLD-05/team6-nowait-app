package com.nowait.domain.owner.dto;

import java.time.LocalDateTime;

import com.nowait.domain.owner.entity.RestaurantOwner;

import lombok.Builder;
import lombok.Getter;

@Getter
public class OwnerResponse {
	
	private final Long id;
	private final Long userId;
	private final Long restaurantId;
	private final LocalDateTime createdAt;
	
	@Builder
	public OwnerResponse(Long id, Long userId, Long restaurantId, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.restaurantId = restaurantId;
		this.createdAt = createdAt;
	}
	
	public static OwnerResponse from(RestaurantOwner owner) {
		return OwnerResponse.builder()
				.id(owner.getId())
				.userId(owner.getUserId())
				.restaurantId(owner.getRestaurantId())
				.createdAt(owner.getCreatedAt())
				.build();
	}

}
