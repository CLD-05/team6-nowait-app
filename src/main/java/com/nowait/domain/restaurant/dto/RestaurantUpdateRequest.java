package com.nowait.domain.restaurant.dto;

<<<<<<< HEAD
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RestaurantUpdateRequest {
	
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
	private String multilingulMenuAvailable;
=======
public class RestaurantUpdateRequest {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
