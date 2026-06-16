package com.nowait.domain.restaurant.entity;

import java.time.LocalTime;

import com.nowait.domain.restaurant.type.DayOfWeek;
import com.nowait.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "restaurant_hours")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantHour extends BaseTimeEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "restaurant_hours_id")
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "restaurant_id", nullable = false)
	private Restaurant restaurant;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 10)
	private DayOfWeek dayOfWeek;
	
	@Column(name = "open_time", nullable = false)
	private LocalTime openTime;
	
	@Column(name = "close_time", nullable = false)
	private LocalTime closeTime;
	
	@Column(name = "is_regular_holiday", nullable = false, columnDefinition = "CHAR(1) DEFAULT 'N'")
	private String isRegularHoliday = "N";
	
	// 🛠️ 점주가 요일별 설정을 변경할 때 사용하는 메서드
    public void updateHour(LocalTime openTime, LocalTime closeTime, String isRegularHoliday) {
    	this.isRegularHoliday = isRegularHoliday;
        // 📜 정기 휴무('Y')면 시간 데이터를 00:00(MIDNIGHT)으로 안전하게 초기화, 아니면 받은 시간 그대로 저장!
        this.openTime = "Y".equals(isRegularHoliday) ? LocalTime.MIDNIGHT : openTime;
        this.closeTime = "Y".equals(isRegularHoliday) ? LocalTime.MIDNIGHT : closeTime;
    }
	
	@Builder
	public RestaurantHour(Restaurant restaurant, DayOfWeek dayOfWeek, LocalTime openTime,
						LocalTime closeTime, String isRegularHoliday) {
		this.restaurant = restaurant;
		this.dayOfWeek = dayOfWeek;
		this.openTime = openTime;
		this.closeTime = closeTime;
		this.isRegularHoliday = (isRegularHoliday != null) ? isRegularHoliday : "N";
	}
	
	

}
