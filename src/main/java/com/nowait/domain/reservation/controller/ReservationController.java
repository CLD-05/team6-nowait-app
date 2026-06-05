package com.nowait.domain.reservation.controller;

<<<<<<< HEAD
public class ReservationController {

}
=======
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

    /**
     * POST /api/v1/reservations
     * 예약 생성 (인증 필요)
     */
    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> createReservation(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReservationResponse response = reservationService.createReservation(
            userDetails.getId(), request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/reservations/me
     * 내 예약 목록 (인증 필요)
     */
    @GetMapping("/reservations/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ReservationResponse> responses = reservationService.getMyReservations(
            userDetails.getId()
        );
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/v1/reservations/{reservationId}
     * 예약 상세 조회 (인증 필요, 본인만)
     */
    @GetMapping("/reservations/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long reservationId
    ) {
        ReservationResponse response = reservationService.getReservation(
            userDetails.getId(), reservationId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/reservations/{reservationId}/cancel
     * 예약 취소 (인증 필요, 본인만)
     */
    @PatchMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long reservationId
    ) {
        ReservationResponse response = reservationService.cancelReservation(
            userDetails.getId(), reservationId
        );
        return ResponseEntity.ok(response);
    }

    // ========== 점주 전용 API ==========

    /**
     * PATCH /api/v1/owner/reservations/{reservationId}/visit
     * 방문 완료 처리 (OWNER 권한)
     */
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping("/owner/reservations/{reservationId}/visit")
    public ResponseEntity<ReservationResponse> markVisited(
        @PathVariable Long reservationId
    ) {
        ReservationResponse response = reservationService.markVisited(reservationId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/owner/reservations/{reservationId}/noshow
     * 노쇼 처리 (OWNER 권한)
     */
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping("/owner/reservations/{reservationId}/noshow")
    public ResponseEntity<ReservationResponse> markNoShow(
        @PathVariable Long reservationId
    ) {
        ReservationResponse response = reservationService.markNoShow(reservationId);
        return ResponseEntity.ok(response);
    }
}
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
