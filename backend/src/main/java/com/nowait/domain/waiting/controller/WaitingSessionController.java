package com.nowait.domain.waiting.controller;

import com.nowait.domain.waiting.dto.WaitingSessionOpenRequest;
import com.nowait.domain.waiting.dto.WaitingSessionResponse;
import com.nowait.domain.waiting.service.WaitingSessionService;
import com.nowait.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WaitingSessionController {

  private final WaitingSessionService waitingSessionService;

  /* 사용자: 세션 현황 조회 */
  @GetMapping("/api/v1/restaurants/{restaurantId}/waiting-session")
  public ResponseEntity<WaitingSessionResponse> getTodaySession(@PathVariable Long restaurantId) {
    return ResponseEntity.ok(waitingSessionService.getTodaySession(restaurantId));
  }

  /* 점주: 세션 오픈 */
  @PreAuthorize("hasRole('OWNER')")
  @PostMapping("/api/v1/owners/restaurants/{restaurantId}/waiting-sessions")
  public ResponseEntity<WaitingSessionResponse> openSession(
      @PathVariable Long restaurantId,
      @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @RequestBody WaitingSessionOpenRequest request) {
    WaitingSessionResponse response = waitingSessionService.openSession(
        restaurantId, principal.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /* 점주: 일시정지 */
  @PreAuthorize("hasRole('OWNER')")
  @PatchMapping("/api/v1/owners/waiting-sessions/{sessionId}/pause")
  public ResponseEntity<WaitingSessionResponse> pauseSession(
      @PathVariable Long sessionId,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingSessionService.pauseSession(sessionId, principal.getUserId()));
  }

  /* 점주: 재개 */
  @PreAuthorize("hasRole('OWNER')")
  @PatchMapping("/api/v1/owners/waiting-sessions/{sessionId}/resume")
  public ResponseEntity<WaitingSessionResponse> resumeSession(
      @PathVariable Long sessionId,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingSessionService.resumeSession(sessionId, principal.getUserId()));
  }

  /* 점주: 마감 */
  @PreAuthorize("hasRole('OWNER')")
  @PatchMapping("/api/v1/owners/waiting-sessions/{sessionId}/close")
  public ResponseEntity<WaitingSessionResponse> closeSession(
      @PathVariable Long sessionId,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingSessionService.closeSession(sessionId, principal.getUserId()));
  }
}