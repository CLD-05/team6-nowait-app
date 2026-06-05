package com.nowait.domain.restaurant.repository;

<<<<<<< HEAD
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.type.RestaurantCategory;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
	
	List<Restaurant> findByCategory(RestaurantCategory category);
	
	List<Restaurant> findByNameContaining(String keyword);
=======
public interface RestaurantRepository {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
