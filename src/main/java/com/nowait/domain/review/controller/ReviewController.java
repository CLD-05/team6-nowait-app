package com.nowait.domain.review.controller;

import com.nowait.domain.review.dto.ReviewCreateRequest;
import com.nowait.domain.review.dto.ReviewResponse;
import com.nowait.domain.review.service.ReviewService;
import com.nowait.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * POST /api/v1/reservations/{reservationToken}/reviews
     * 리뷰 작성 (인증 필요, 방문 완료 상태에서만 가능)
     * reservationToken(UUID) 기반 — DB sync 전에도 작성 가능
     */
    @PostMapping("/api/v1/reservations/{reservationToken}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String reservationToken,
        @Valid @RequestBody ReviewCreateRequest request
    ) {
        ReviewResponse response = reviewService.createReview(
            userDetails.getUserId(), reservationToken, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/restaurants/{restaurantId}/reviews
     * 식당 리뷰 목록 (공개)
     */
    @GetMapping("/api/v1/restaurants/{restaurantId}/reviews")
    public ResponseEntity<List<ReviewResponse>> getRestaurantReviews(
        @PathVariable Long restaurantId
    ) {
        return ResponseEntity.ok(reviewService.getRestaurantReviews(restaurantId));
    }

    /**
     * GET /api/users/me/reviews
     * 내 리뷰 목록 (인증 필요)
     */
    @GetMapping("/api/v1/users/me/reviews")
    public ResponseEntity<List<ReviewResponse>> getMyReviews(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(reviewService.getMyReviews(userDetails.getUserId()));
    }

    /**
     * PATCH /api/reviews/{reviewId}
     * 리뷰 수정 (본인만)
     */
    @PatchMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long reviewId,
        @Valid @RequestBody ReviewCreateRequest request
    ) {
        ReviewResponse response = reviewService.updateReview(
            userDetails.getUserId(), reviewId, request
        );
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/reviews/{reviewId}
     * 리뷰 삭제 (본인만)
     */
    @DeleteMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(userDetails.getUserId(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
