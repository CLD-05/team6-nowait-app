package com.nowait.domain.review.repository;

import com.nowait.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 식당 리뷰 목록 (최신순)
    List<Review> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    // 내 리뷰 목록 (최신순)
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 한 예약에 대한 리뷰 중복 여부
    boolean existsByReservationId(Long reservationId);
}
