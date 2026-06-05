package com.nowait.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.user.dto.UserResponse;
import com.nowait.domain.user.dto.UserUpdateRequest;
import com.nowait.domain.user.service.UserService;
import com.nowait.global.security.principal.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	
	private final UserService userService;
	
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
	
	Long loggedInUserId = userDetails.getUserId();
	
	UserResponse response = userService.getUserInfo(loggedInUserId);
	
	return ResponseEntity.ok(response);
	}
	
	@PatchMapping("/me")
	public ResponseEntity<UserResponse> patchMyProfile(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@jakarta.validation.Valid @RequestBody UserUpdateRequest request) {
		
		Long loggedInUserId = userDetails.getUserId();
		
		UserResponse updatedResponse = userService.updateUserInfo(loggedInUserId, request);
		
		return ResponseEntity.ok(updatedResponse);
	}
}
