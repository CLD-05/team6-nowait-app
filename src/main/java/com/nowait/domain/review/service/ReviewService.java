package com.nowait.domain.review.service;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.repository.ReservationRepository;
import com.nowait.domain.reservation.type.ReservationStatus;
import com.nowait.domain.review.dto.ReviewCreateRequest;
import com.nowait.domain.review.dto.ReviewResponse;
import com.nowait.domain.review.entity.Review;
import com.nowait.domain.review.repository.ReviewRepository;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 리뷰 작성
     * 1. 본인 예약인지 확인
     * 2. 예약 상태가 VISITED 인지 확인
     * 3. 동일 예약 중복 작성 차단
     */
    @Transactional
    public ReviewResponse createReview(Long userId, Long reservationId, ReviewCreateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        if (reservation.getStatus() != ReservationStatus.VISITED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_VISITED);
        }

        if (reviewRepository.existsByReservationId(reservationId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        LocalDateTime visitedAt = LocalDateTime.of(
            reservation.getSlot().getSlotDate(),
            reservation.getSlot().getSlotTime()
        );

        Review review = Review.builder()
            .user(user)
            .restaurant(reservation.getRestaurant())
            .reservation(reservation)
            .rating(request.rating())
            .content(request.content())
            .visitedAt(visitedAt)
            .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    /**
     * 식당 리뷰 목록 조회
     */
    public List<ReviewResponse> getRestaurantReviews(Long restaurantId) {
        return reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .stream()
            .map(ReviewResponse::from)
            .toList();
    }

    /**
     * 내 리뷰 목록 조회
     */
    public List<ReviewResponse> getMyReviews(Long userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(ReviewResponse::from)
            .toList();
    }

    /**
     * 리뷰 수정 (본인만)
     */
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, ReviewCreateRequest request) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        review.update(request.rating(), request.content());
        return ReviewResponse.from(review);
    }

    /**
     * 리뷰 삭제 (본인만)
     */
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        reviewRepository.delete(review);
    }
}
