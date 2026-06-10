package com.nowait.domain.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.type.DayOfWeek;

public interface RestaurantHourRepository extends JpaRepository<RestaurantHour, Long>{
	
	Optional<RestaurantHour> findByRestaurantIdAndDayOfWeek(Long restaurantId, DayOfWeek dayOfWeek);

	List<RestaurantHour> findByRestaurantId(Long restaurantId);
}
