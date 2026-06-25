package com.nowait.domain.waiting.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.waiting.dto.WaitingCallLogResponse;
import com.nowait.domain.waiting.service.WaitingService;
import com.nowait.global.security.principal.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OwnerWaitingController {

	private final WaitingService waitingService;

	@PreAuthorize("hasRole('OWNER')")
	@GetMapping("/api/owners/waiting/{waitingId}/call-logs")
	public ResponseEntity<List<WaitingCallLogResponse>> getWaitingCallLogs(
			@PathVariable Long waitingId,
			@AuthenticationPrincipal CustomUserDetails userDetails
			) {
		
		List<WaitingCallLogResponse> responses = waitingService.getCallLogs(waitingId, userDetails.getUserId());
		
		return ResponseEntity.ok(responses);
	}

}
