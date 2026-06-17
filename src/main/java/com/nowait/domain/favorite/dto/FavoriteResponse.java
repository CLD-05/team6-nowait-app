package com.nowait.domain.favorite.dto;

import java.time.LocalDateTime;

import com.nowait.domain.favorite.entity.Favorite;

import lombok.Getter;

@Getter
public class FavoriteResponse {
	
	private final Long favoriteId;
	private final Long restaurantId;
	private final String restaurantName;
	private final String mainCategory;
	private final String category;
	private final String mainMenuName;
	private final String imageUrl;
	private final LocalDateTime createdAt;
	
	public FavoriteResponse(Favorite favorite, String resolvedImageUrl) {

		this.favoriteId = favorite.getFavoriteId();
		this.restaurantId = favorite.getRestaurant().getId();
		this.restaurantName = favorite.getRestaurant().getName();
		this.mainCategory = favorite.getRestaurant().getCategory().name();
		this.category = favorite.getRestaurant().getCategory().name();
		this.mainMenuName = favorite.getRestaurant().getMainMenuName();
		this.imageUrl = resolvedImageUrl;
		this.createdAt = favorite.getCreatedAt();
	}

}
