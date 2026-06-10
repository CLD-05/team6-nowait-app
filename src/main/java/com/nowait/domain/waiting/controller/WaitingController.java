package com.nowait.domain.waiting.controller;

import com.nowait.domain.waiting.dto.WaitingRegisterRequest;
import com.nowait.domain.waiting.dto.WaitingResponse;
import com.nowait.domain.waiting.service.WaitingService;
import com.nowait.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WaitingController {

  private final WaitingService waitingService;

  /* 사용자: 웨이팅 등록 */
  @PreAuthorize("hasRole('USER')")
  @PostMapping("/api/restaurants/{restaurantId}/waitings")
  public ResponseEntity<WaitingResponse> register(
      @PathVariable Long restaurantId,
      @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @RequestBody WaitingRegisterRequest request) {
    WaitingResponse response = waitingService.register(
        restaurantId, principal.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /* 사용자: 내 웨이팅 조회 */
  @GetMapping("/api/waitings/me")
  public ResponseEntity<WaitingResponse> getMyWaiting(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingService.getMyWaiting(principal.getUserId()));
  }

  /* 사용자: 내 웨이팅 취소 (토큰 기반) */
  @PatchMapping("/api/waitings/{token}/cancel")
  public ResponseEntity<WaitingResponse> cancelMyWaiting(
      @PathVariable String token,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingService.cancelByUser(token, principal.getUserId()));
  }

  /* 점주: 식당의 웨이팅 목록 조회 */
  @PreAuthorize("hasRole('OWNER')")
  @GetMapping("/api/owners/restaurants/{restaurantId}/waitings")
  public ResponseEntity<List<WaitingResponse>> getOwnerWaitings(
      @PathVariable Long restaurantId,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingService.getOwnerWaitings(restaurantId, principal.getUserId()));
  }

  /* 점주: 호출 (토큰 기반) */
@PreAuthorize("hasRole('OWNER')")
@PatchMapping(value = {
    "/api/owners/waiting/{token}/call",
    "/api/owners/waiting/{token}/recall"
})
public ResponseEntity<WaitingResponse> call(
    @PathVariable String token,
    @AuthenticationPrincipal CustomUserDetails principal) {
  return ResponseEntity.ok(waitingService.call(token, principal.getUserId()));
}

  /* 점주: 취소 처리 (토큰 기반) */
  @PreAuthorize("hasRole('OWNER')")
  @PatchMapping("/api/owners/waiting/{token}/cancelled")
  public ResponseEntity<WaitingResponse> cancelByOwner(
      @PathVariable String token,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingService.cancelByOwner(token, principal.getUserId()));
  }

  /* 점주: 입장 처리 (토큰 기반) */
  @PreAuthorize("hasRole('OWNER')")
  @PatchMapping("/api/owners/waiting/{token}/enter")
  public ResponseEntity<WaitingResponse> enter(
      @PathVariable String token,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingService.markEntered(token, principal.getUserId()));
  }

  /* 점주: 입장 완료 처리 (현재 enter 와 동일 동작) */
  @PreAuthorize("hasRole('OWNER')")
  @PatchMapping("/api/owners/waiting/{token}/entered")
  public ResponseEntity<WaitingResponse> entered(
      @PathVariable String token,
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(waitingService.markEntered(token, principal.getUserId()));
  }
}
