package com.nowait.domain.review.entity;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.user.entity.User;
import com.nowait.global.common.BaseTimeEntity;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "review",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_review_reservation_id",
        columnNames = "reservation_id"
    ),
    indexes = {
        @Index(name = "idx_review_restaurant_id", columnList = "restaurant_id, created_at"),
        @Index(name = "idx_review_user_id", columnList = "user_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "visited_at", nullable = false)
    private LocalDateTime visitedAt;

    @Builder
    public Review(User user, Restaurant restaurant, Reservation reservation,
                  int rating, String content, LocalDateTime visitedAt) {
        validateRating(rating);
        this.user = user;
        this.restaurant = restaurant;
        this.reservation = reservation;
        this.rating = rating;
        this.content = content;
        this.visitedAt = visitedAt;
    }

    public void update(int rating, String content) {
        validateRating(rating);
        this.rating = rating;
        this.content = content;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "평점은 1~5 사이여야 합니다.");
        }
    }
}
