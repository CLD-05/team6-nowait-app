package com.nowait.domain.owner.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.owner.dto.OwnerRegisterRequest;
import com.nowait.domain.owner.dto.OwnerResponse;
import com.nowait.domain.owner.dto.OwnerUpdateRequest;
import com.nowait.domain.owner.service.OwnerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/owners")
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
	
	@GetMapping("/me/restaurant")
	public ResponseEntity<OwnerResponse> getMyRestaurant(@AuthenticationPrincipal Long loginUserId) {
		
		OwnerResponse response = ownerService.getOwnerRestaurant(loginUserId);
		return ResponseEntity.ok(response);
	}
	
    /**
     * 점주 권한 해제
     * DELETE /api/owners/me
     */
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/me")
    public ResponseEntity<String> deleteOwner(@AuthenticationPrincipal Long loginUserId) {
        ownerService.deleteOwner(loginUserId);
        return ResponseEntity.ok("점주 권한이 해제되었습니다.");
    }
}
