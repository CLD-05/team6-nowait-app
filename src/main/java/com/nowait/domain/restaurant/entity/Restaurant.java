package com.nowait.domain.restaurant.entity;

<<<<<<< HEAD
import java.time.LocalTime;

import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant extends BaseTimeEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "restaurant_id")
	private Long id;
	
	@Column(name = "owner_id", nullable = false)
	private Long ownerId;
	
	@Column(name = "restaurant_name", nullable = false, length = 100)
	private String name;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RestaurantCategory category;
	
	@Column(nullable = false, length = 255)
	private String address;
	
	@Column(name = "phone_number", length = 20)
	private String phoneNumber;
	
	@Lob
	private String description;
	
	@Column(name = "image_url", length = 50)
	private String imageUrl;
	
	@Column(name = "main_menu_name", length = 255)
	private String mainMenuName;
	
	@Column(name = "open_time", nullable = false)
	private LocalTime openTime;
	
	@Column(name = "close_time", nullable = false)
	private LocalTime closeTime;
	
	@Column(name = "closed_days", length = 100)
	private String closedDays;
	
	@Column(name = "parking_available", length = 1)
	private String parkingAvailable = "N";
	
	@Column(name = "wifi_available", length = 1)
	private String wifiAvailable = "N";
	
	@Column(name = "multilingual_menu_available", length = 1)
	private String multilingualMenuAvailable = "N";
	
	@Builder
	public Restaurant(Long ownerId, String name, RestaurantCategory category, String address, String phoneNumber,
			String description, String imageUrl, String mainMenuName, LocalTime openTime, LocalTime closeTime,
			String closedDays, String parkingAvailable, String wifiAvailable, String multilingualMenuAvailable) {
		this.ownerId = ownerId;
		this.name = name;
		this.category = category;
		this.address = address;
		this.phoneNumber = phoneNumber;
		this.description = description;
		this.imageUrl = imageUrl;
		this.mainMenuName = mainMenuName;
		this.openTime = openTime;
		this.closeTime = closeTime;
		this.closedDays = closedDays;
		this.parkingAvailable = parkingAvailable != null ? parkingAvailable : "N";
		this.wifiAvailable = wifiAvailable != null ? wifiAvailable : "N";
		this.multilingualMenuAvailable = multilingualMenuAvailable != null ? multilingualMenuAvailable : "N";
	}
=======
public class Restaurant {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
