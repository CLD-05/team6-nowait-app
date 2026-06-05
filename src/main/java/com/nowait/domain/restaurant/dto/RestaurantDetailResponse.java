package com.nowait.domain.restaurant.dto;

import java.time.LocalTime;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.type.RestaurantCategory;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RestaurantDetailResponse {
	
	private Long id;
	private String name;
	private RestaurantCategory category;
	private String address;
	private String phoneNumber;
	private String description;
	private String imageUrl;
	private String mainMenuName;
	private LocalTime openTime;
	private LocalTime closeTime;
	private String closedDays;
	private String parkingAvailable;
	private String wifiAvailable;
	private String multilingualMenuAvailable;
	
	public static RestaurantDetailResponse from(Restaurant restaurant) {
		return new RestaurantDetailResponse(
				restaurant.getId(),
				restaurant.getName(),
				restaurant.getCategory(),
				restaurant.getAddress(),
				restaurant.getPhoneNumber(),
				restaurant.getDescription(),
				restaurant.getImageUrl(),
				restaurant.getMainMenuName(),
				restaurant.getOpenTime(),
				restaurant.getCloseTime(),
				restaurant.getClosedDays(),
				restaurant.getParkingAvailable(),
				restaurant.getWifiAvailable(),
				restaurant.getMultilingualMenuAvailable()
				);
		
	}

}
