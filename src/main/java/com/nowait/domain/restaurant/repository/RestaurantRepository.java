package com.nowait.domain.restaurant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.type.RestaurantCategory;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
	
	List<Restaurant> findByCategory(RestaurantCategory category);
	
	List<Restaurant> findByNameContaining(String keyword);

	List<Restaurant> findByOwnerIdAndIsDeleted(Long userId, String string);

}
