package com.nowait.domain.restaurant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.restaurant.dto.RestaurantHourRequest;
import com.nowait.domain.restaurant.service.RestaurantHourService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owners/restaurants/{restaurantId}/hours")
public class RestaurantHourController {
	
	private final RestaurantHourService restaurantHourService;
	
	/**
     * 🔍 1. 점주 전용: 설정된 요일별 영업시간 조회 (GET)
     * 주소 예시: GET /api/owner/restaurants/1/hours
     * 역할: 사장님이 수정 화면 켰을 때, 기존에 등록된 7일치 데이터를 화면에 로딩해 줌
     */
	@GetMapping
	public ResponseEntity<List<RestaurantHourRequest>> getRestaurantHours(
			@PathVariable Long restaurantId
			) {
		List<RestaurantHourRequest> response = restaurantHourService.getRestaurantHours(restaurantId);
		return ResponseEntity.ok(response);
	}
	
	/**
     * 🛠️ 2. 점주 전용: 요일별 영업시간 설정 변경 및 등록 (POST - Upsert)
     * 주소 예시: POST /api/owner/restaurants/1/hours
     * 역할: 사장님이 화면에서 [저장] 눌렀을 때, 7일치 리스트를 받아서 수정/등록을 알아서 처리함
     */
	@PostMapping
	public ResponseEntity<Void> saveOrUpdateRestaurantHours(
			@PathVariable Long restaurantId,
			@Valid @RequestBody List<RestaurantHourRequest> request
			) {
		restaurantHourService.saveOrUpdateRestaurantHours(restaurantId, request);
		return ResponseEntity.status(HttpStatus.OK).build();
	}
}
