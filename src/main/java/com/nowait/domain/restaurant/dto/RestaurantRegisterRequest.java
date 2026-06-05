package com.nowait.domain.restaurant.dto;

import java.time.LocalTime;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.type.RestaurantCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RestaurantRegisterRequest {
	
	@NotBlank(message = "식당 이름은 필수 입력 항목입니다.")
	private String name;
	
	@NotNull(message = "식당 카테고리는 필수 선택 항목입니다.")
	private RestaurantCategory category;
	
	@NotBlank(message = "식당 주소는 필수 입력 항목입니다.")
	private String address;
	
	private String phoneNumber;
	
	private String description;
	
	private String imageUrl;
	
	private String mainMenuName;
	
	@NotNull(message = "영업 시작 시간은 필수 입력 항목입니다.")
	private LocalTime openTime;
	
	@NotNull(message = "영업 종료 시간은 필수 입력 항목입니다.")
	private LocalTime closeTime;
	
	private String closedDays;
	
	private String parkingAvailable;
	
	private String wifiAvailable;
	
	private String multilingualMenuAvailable;
	
	public Restaurant toEntity(Long ownerId) {
		return Restaurant.builder()
				.name(this.name)
				.category(this.category)
				.address(this.address)
				.phoneNumber(this.phoneNumber)
				.description(this.description)
				.imageUrl(this.imageUrl)
				.mainMenuName(this.mainMenuName)
				.openTime(this.openTime)
				.closeTime(this.closeTime)
				.closedDays(this.closedDays)
				.parkingAvailable(this.parkingAvailable)
				.wifiAvailable(this.wifiAvailable)
				.multilingualMenuAvailable(this.multilingualMenuAvailable)
				.build();
	}

}

