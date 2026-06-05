package com.nowait.domain.owner.entity;

<<<<<<< HEAD
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant_owners ")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantOwner {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;
	
	@Column(name = "user_id", nullable = false, unique = true)
	private Long userId;
	
	@Column(name = "restaurant_id", nullable = false, unique = true)
	private Long restaurantId;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Builder
	public RestaurantOwner(Long userId, Long restaurantId) {
		this.userId = userId;
		this.restaurantId = restaurantId;
		this.createdAt = LocalDateTime.now();
		
	}
=======
public class RestaurantOwner {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
