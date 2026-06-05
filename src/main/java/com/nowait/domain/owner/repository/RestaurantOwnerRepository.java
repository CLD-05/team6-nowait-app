package com.nowait.domain.owner.repository;

public interface RestaurantOwnerRepository {
  boolean existsByUserIdAndRestaurantId(Long userId, Long restaurantId);
}
