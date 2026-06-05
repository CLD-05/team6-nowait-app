package com.nowait.domain.restaurant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.restaurant.dto.RestaurantDetailResponse;
import com.nowait.domain.restaurant.dto.RestaurantListResponse;
import com.nowait.domain.restaurant.dto.RestaurantRegisterRequest;
import com.nowait.domain.restaurant.dto.RestaurantUpdateRequest;
import com.nowait.domain.restaurant.service.RestaurantService;
import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.global.security.principal.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restaurants")
public class RestaurantController {
	
	private final RestaurantService restaurantService;
	
	@PostMapping
	public ResponseEntity<Long> registerRestaurant(
			@Valid @RequestBody RestaurantRegisterRequest request,
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		Long restaurantId = restaurantService.registerRestaurant(request, userDetails.getUserId());
		return ResponseEntity.status(HttpStatus.CREATED).body(restaurantId);
	}
	
	@GetMapping
	public ResponseEntity<List<RestaurantListResponse>> getRestaurants(
			@RequestParam(value = "category", required = false) RestaurantCategory category,
			@RequestParam(value = "keyword", required = false) String keyword
			) {
		List<RestaurantListResponse> responses;
		
		if (keyword != null && !keyword.trim().isEmpty()) {
			responses = restaurantService.searchRestaurantsByName(keyword);
		} else if (category != null) {
			responses = restaurantService.getRestaurantsByCategory(category);
		} else {
			responses = restaurantService.getAllRestaurants();
		}
		return ResponseEntity.ok(responses);
	}
	
	@GetMapping("/{restaurantId}")
	public ResponseEntity<RestaurantDetailResponse> getRestaurantDetail(
			@PathVariable("restaurantId") Long restaurantId
			) {
		RestaurantDetailResponse response = restaurantService.getRestaurantDetail(restaurantId);
		return ResponseEntity.ok(response);
	}
	
	@PutMapping("/{restaurantId}")
	public ResponseEntity<Void> updateRestaurant(
			@PathVariable("restaurantId") Long restaurantId,
			@Valid @RequestBody RestaurantUpdateRequest request,
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		restaurantService.updateRestaurant(restaurantId, request, userDetails.getUserId());
		return ResponseEntity.ok().build();
	}
}
