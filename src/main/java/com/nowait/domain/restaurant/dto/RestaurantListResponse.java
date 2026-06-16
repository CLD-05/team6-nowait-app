package com.nowait.domain.restaurant.dto;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.domain.restaurant.type.RestaurantStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RestaurantListResponse {
	
	private Long id;
	private String name;
	private RestaurantCategory category;
	private String address;
	private String imageUrl;
	private String mainMenuName;
	private RestaurantStatus status;
	private String reservationAvailable;
	private String waitingAvailable;
	
	public static RestaurantListResponse from(Restaurant restaurant, String imageUrl) {
		return new RestaurantListResponse(
				restaurant.getId(),
				restaurant.getName(),
				restaurant.getCategory(),
				restaurant.getAddress(),
				imageUrl,
				restaurant.getMainMenuName(),
				restaurant.getStatus(),
				restaurant.getReservationAvailable(),
				restaurant.getWaitingAvailable()
		);
	}
}
