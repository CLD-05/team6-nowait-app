package com.nowait.domain.review.dto;

import com.nowait.domain.review.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponse(

    Long reviewId,
    Long userId,
    String userName,
    Long restaurantId,
    String restaurantName,
    Long reservationId,
    int rating,
    String content,
    LocalDateTime visitedAt,
    LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
            review.getId(),
            review.getUser().getId(),
            review.getUser().getName(),
            review.getRestaurant().getId(),
            review.getRestaurant().getName(),
            review.getReservation().getId(),
            review.getRating(),
            review.getContent(),
            review.getVisitedAt(),
            review.getCreatedAt()
        );
    }
}
