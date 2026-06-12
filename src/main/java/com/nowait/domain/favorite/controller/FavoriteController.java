package com.nowait.domain.favorite.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.favorite.dto.FavoriteResponse;
import com.nowait.domain.favorite.service.FavoriteService;
import com.nowait.global.security.principal.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FavoriteController {
	
	private final FavoriteService favoriteService;
	
	@GetMapping("/api/users/me/favorites")
	public ResponseEntity<List<FavoriteResponse>> getMyFavorites(
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		List<FavoriteResponse> responses = favoriteService.getMyFavorites(userDetails.getUserId());
		return ResponseEntity.ok(responses);
	}
	
	@PostMapping("/api/restaurants/{restaurantId}/favorite")
	public ResponseEntity<String> toggleFavorite(
			@PathVariable Long restaurantId,
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		String message = favoriteService.toggleFavorite(userDetails.getUserId(), restaurantId);
		return ResponseEntity.ok(message);
	}
}
