package com.nowait.domain.reservation.controller;

import com.nowait.domain.reservation.dto.ReservationCreateRequest;
import com.nowait.domain.reservation.dto.ReservationResponse;
import com.nowait.domain.reservation.service.ReservationService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /* POST /api/v1/reservations — 예약 생성 */
    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> createReservation(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReservationResponse response = reservationService.createReservation(
            userDetails.getUserId(), request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /* GET /api/v1/reservations/me — 내 예약 목록 */
    @GetMapping("/reservations/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(reservationService.getMyReservations(userDetails.getUserId()));
    }

    /* GET /api/v1/reservations/{token} — 예약 상세 (본인) */
    @GetMapping("/reservations/{token}")
    public ResponseEntity<ReservationResponse> getReservation(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String token
    ) {
        return ResponseEntity.ok(
            reservationService.getReservation(userDetails.getUserId(), token));
    }

    /* PATCH /api/v1/reservations/{token}/cancel — 예약 취소 (본인) */
    @PatchMapping("/reservations/{token}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String token
    ) {
        return ResponseEntity.ok(
            reservationService.cancelReservation(userDetails.getUserId(), token));
    }

    // ========== 점주 전용 API ==========

    /* PATCH /api/v1/owner/reservations/{token}/visit — 방문 완료 */
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping("/owner/reservations/{token}/visit")
    public ResponseEntity<ReservationResponse> markVisited(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String token
    ) {
        return ResponseEntity.ok(
            reservationService.markVisited(userDetails.getUserId(), token));
    }

    /* PATCH /api/v1/owner/reservations/{token}/noshow — 노쇼 처리 */
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping("/owner/reservations/{token}/noshow")
    public ResponseEntity<ReservationResponse> markNoShow(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String token
    ) {
        return ResponseEntity.ok(
            reservationService.markNoShow(userDetails.getUserId(), token));
    }
}
