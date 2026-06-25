package com.nowait.domain.restaurant.dto;

import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.domain.restaurant.type.RestaurantStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RestaurantUpdateRequest {

	@NotBlank
	private String name;

	@NotNull
	private RestaurantCategory category;

	@NotBlank
	private String address;

	private String phoneNumber;
	private String description;
	private String imageUrl;
	private String mainMenuName;
	private String parkingAvailable;
	private String wifiAvailable;
	private String multilingualMenuAvailable;

	@NotNull
	private RestaurantStatus status;

	private String reservationAvailable;
	private String waitingAvailable;
}
