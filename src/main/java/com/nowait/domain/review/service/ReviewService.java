package com.nowait.domain.review.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationRedisLuaExecutor reservationRedis;

    /**
     * 리뷰 작성 — reservationToken(UUID) 기반.
     *
     * DB 레코드는 항상 존재하지만 상태(status)가 Redis에만 VISITED로 반영된 경우를 처리:
     *   - DB 레코드 조회 후 실제 상태를 Redis에서 최신값으로 보정
     *   - DB에 레코드 자체가 없는 경우(극히 드문 Worker 지연) → 재시도 안내 메시지
     */
    @Transactional
    @CacheEvict(value = "restaurant_reviews", key = "#reservationRedis.findByToken(#reservationToken).restaurantId()", cacheManager = "cacheManager")
    public ReviewResponse createReview(Long userId, String reservationToken, ReviewCreateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Redis에서 실제 최신 상태 먼저 확인
        ReservationTokenData redisData = reservationRedis.findByToken(reservationToken);

        Reservation reservation = reservationRepository.findByReservationToken(reservationToken)
            .orElse(null);

        if (reservation == null) {
            // Redis에 VISITED 상태로 존재하면 Worker 동기화 대기 안내
            if (redisData != null && redisData.status() == ReservationStatus.VISITED) {
                throw new BusinessException(ErrorCode.RESERVATION_SYNC_IN_PROGRESS);
            }
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        // Redis 최신 상태 우선, 없으면 DB 상태 사용
        ReservationStatus actualStatus = (redisData != null) ? redisData.status() : reservation.getStatus();

        if (actualStatus != ReservationStatus.VISITED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_VISITED);
        }

        if (reviewRepository.existsByReservationId(reservation.getId())) {
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
     * 🍕 식당 리뷰 목록 조회 (캐싱 적용)
     * - value="restaurant_reviews": 식당별 리뷰들을 모아두는 임의의 바구니 이름
     * - key="#restaurantId": 식당 번호별로 캐시 장부를 쪼개서 보관합니다. (예: restaurant_reviews::3)
     */
    @Cacheable(value = "restaurant_reviews", key = "#restaurantId", cacheManager = "cacheManager")
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
     * ✏️ 리뷰 수정 (본인만)
     * - 사장님이 지우거나 유저가 수정하면 바구니 안의 특정 식당 캐시를 정확히 타격해서 리셋합니다.
     */
    @Transactional
    @CacheEvict(value = "restaurant_reviews", key = "#reviewRepository.findById(#reviewId).orElse(null)?.restaurant?.id", cacheManager = "cacheManager")
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
     * 🗑️ 리뷰 삭제 (본인만)
     */
    @Transactional
    @CacheEvict(value = "restaurant_reviews", key = "#reviewRepository.findById(#reviewId).orElse(null)?.restaurant?.id", cacheManager = "cacheManager")
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        reviewRepository.delete(review);
    }
}
