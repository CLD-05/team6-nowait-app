package com.nowait.domain.restaurant.entity;

import java.time.LocalTime;

import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.domain.restaurant.type.RestaurantStatus;
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
@org.hibernate.annotations.SQLRestriction("is_deleted = 'N'")
public class Restaurant extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "restaurant_id")
	private Long id;

	@Column(name = "owner_id")
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

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "main_menu_name", length = 255)
	private String mainMenuName;

	@Column(name = "parking_available", length = 1)
	private String parkingAvailable = "N";

	@Column(name = "wifi_available", length = 1)
	private String wifiAvailable = "N";

	@Column(name = "multilingual_menu_available", length = 1)
	private String multilingualMenuAvailable = "N";

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RestaurantStatus status = RestaurantStatus.CLOSED;

	@Column(name = "reservation_available", nullable = false, columnDefinition = "CHAR(1) DEFAULT 'Y'")
	private String reservationAvailable = "Y";

	@Column(name = "waiting_available", nullable = false, columnDefinition = "CHAR(1) DEFAULT 'Y'")
	private String waitingAvailable = "Y";

	@Column(name = "is_deleted", nullable = false, columnDefinition = "CHAR(1) DEFAULT 'N'")
	private String isDeleted = "N";

	// 🛎️ 점주가 설정을 바꿀 때 쓸 메서드
	public void updateAvailableStatus(String reservationAvailable, String waitingAvailable) {
		this.reservationAvailable = reservationAvailable;
		this.waitingAvailable = waitingAvailable;
	}

	public void updateStatus(RestaurantStatus newStatus) {
		this.status = newStatus;
	}

	public void updateImage(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public void deleteRestaurant() {
		this.isDeleted = "Y";
		this.status = RestaurantStatus.PERMANENTLY_CLOSED; // 💡 영업 상태도 영구 폐업으로 자동 전환!
	}

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
		this.parkingAvailable = parkingAvailable != null ? parkingAvailable : "N";
		this.wifiAvailable = wifiAvailable != null ? wifiAvailable : "N";
		this.multilingualMenuAvailable = multilingualMenuAvailable != null ? multilingualMenuAvailable : "N";
	}
}
