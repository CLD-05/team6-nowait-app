package com.nowait.domain.owner.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.owner.dto.OwnerRegisterRequest;
import com.nowait.domain.owner.dto.OwnerResponse;
import com.nowait.domain.owner.service.OwnerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {
	
	private final OwnerService ownerService;
	
	@PostMapping
	public ResponseEntity<OwnerResponse> registerOwner(
			@Valid @RequestBody OwnerRegisterRequest request
			) {
		OwnerResponse response = ownerService.registerOwner(request);
		
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
	
	@GetMapping
	public ResponseEntity<OwnerResponse> getMyRestaurant(@AuthenticationPrincipal Long loginUserId) {
		
		OwnerResponse response = ownerService.getOwnerRestaurant(loginUserId);
		return ResponseEntity.ok(response);
	}
}
