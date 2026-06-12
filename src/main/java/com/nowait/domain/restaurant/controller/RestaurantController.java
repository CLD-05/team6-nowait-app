package com.nowait.domain.restaurant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.restaurant.dto.RestaurantDetailResponse;
import com.nowait.domain.restaurant.dto.RestaurantImageRequest;
import com.nowait.domain.restaurant.dto.RestaurantHourRequest;
import com.nowait.domain.restaurant.dto.RestaurantListResponse;
import com.nowait.domain.restaurant.dto.RestaurantRegisterRequest;
import com.nowait.domain.restaurant.dto.RestaurantUpdateRequest;
import com.nowait.domain.restaurant.service.RestaurantService;
import com.nowait.domain.restaurant.service.RestaurantHourService;
import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.global.security.principal.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {
	
	private final RestaurantService restaurantService;
	private final RestaurantHourService restaurantHourService;
	
	@PreAuthorize("hasRole('OWNER')")
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

	@PreAuthorize("hasRole('OWNER')")
	@GetMapping("/mine")
	public ResponseEntity<RestaurantDetailResponse> getMyRestaurant(
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		return ResponseEntity.ok(restaurantService.getMyRestaurant(userDetails.getUserId()));
	}
	
	@GetMapping("/{restaurantId}")
	public ResponseEntity<RestaurantDetailResponse> getRestaurantDetail(
			@PathVariable("restaurantId") Long restaurantId
			) {
		RestaurantDetailResponse response = restaurantService.getRestaurantDetail(restaurantId);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{restaurantId}/hours")
	public ResponseEntity<List<RestaurantHourRequest>> getRestaurantHours(
			@PathVariable("restaurantId") Long restaurantId
			) {
		return ResponseEntity.ok(restaurantHourService.getRestaurantHours(restaurantId));
	}
	
	@PreAuthorize("hasRole('OWNER')")
	@PutMapping("/{restaurantId}")
	public ResponseEntity<Void> updateRestaurant(
			@PathVariable("restaurantId") Long restaurantId,
			@Valid @RequestBody RestaurantUpdateRequest request,
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		restaurantService.updateRestaurant(restaurantId, request, userDetails.getUserId());
		return ResponseEntity.ok().build();
	}
	
	@PreAuthorize("hasRole('OWNER')")
	@PatchMapping("/{restaurantId}/status")
	public ResponseEntity<Void> updateStatus(
			@PathVariable Long restaurantId,
			@RequestBody java.util.Map<String, String> body,
			@AuthenticationPrincipal CustomUserDetails userDetails
	) {
		com.nowait.domain.restaurant.type.RestaurantStatus status =
				com.nowait.domain.restaurant.type.RestaurantStatus.valueOf(body.get("status"));
		restaurantService.updateStatus(restaurantId, status, userDetails.getUserId());
		return ResponseEntity.ok().build();
	}

	@PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<String> deleteRestaurant(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        // 아직 DB 칼럼은 안 바꿨지만, 서비스에 길만 먼저 뚫어놓습니다!
        restaurantService.deleteRestaurant(restaurantId, userDetails.getUserId());
        return ResponseEntity.ok("식당 삭제(비활성화)가 완료되었습니다.");
    }
	
	@PreAuthorize("hasRole('OWNER')")
	@PostMapping("/{restaurantId}/image") // 이미지 추가
    public ResponseEntity<String> registerRestaurantImage(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody RestaurantImageRequest request) {
        
        restaurantService.updateRestaurantImage(restaurantId, userDetails.getUserId(), request.getImageUrl());
        return ResponseEntity.ok("식당 이미지 주소 등록 성공");
    }
	
	@PreAuthorize("hasRole('OWNER')")
	@PatchMapping("/{restaurantId}/image")
    public ResponseEntity<String> updateRestaurantImage(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody RestaurantImageRequest request) { // 이제 파일이 아니라 JSON 글자로 받음!
        
        restaurantService.updateRestaurantImage(restaurantId, userDetails.getUserId(), request.getImageUrl());
        return ResponseEntity.ok("식당 이미지 주소 업데이트 성공");
    }
	
	@PreAuthorize("hasRole('OWNER')")
	@DeleteMapping("/{restaurantId}/image")
    public ResponseEntity<String> deleteRestaurantImage(
    		@PathVariable Long restaurantId, 
    		@AuthenticationPrincipal CustomUserDetails userDetails) {
		
        restaurantService.deleteRestaurantImage(restaurantId, userDetails.getUserId());
        return ResponseEntity.ok("식당 이미지 주소 삭제 성공");
    }
}
