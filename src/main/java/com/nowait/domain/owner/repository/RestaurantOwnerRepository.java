package com.nowait.domain.owner.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nowait.domain.owner.entity.RestaurantOwner;

public interface RestaurantOwnerRepository extends JpaRepository<RestaurantOwner, Long>{
	
	boolean existsByUserId(Long userId);
	
	boolean existsByRestaurantId(Long restaurantId);
	
	boolean existsByUserIdAndRestaurantId(Long userId, Long restaurantId);
	
	java.util.Optional<RestaurantOwner> findByUserId(Long userId);
}
