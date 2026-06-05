package com.nowait.domain.restaurant.dto;

<<<<<<< HEAD
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.type.RestaurantCategory;

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
	
	public static RestaurantListResponse from(Restaurant restaurant) {
		return new RestaurantListResponse(
				restaurant.getId(),
				restaurant.getName(),
				restaurant.getCategory(),
				restaurant.getAddress(),
				restaurant.getImageUrl(),
				restaurant.getMainMenuName()
				);
	}
=======
public class RestaurantListResponse {

>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
}
