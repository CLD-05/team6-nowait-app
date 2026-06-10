package com.nowait.domain.restaurant.dto;

import java.time.LocalTime;

import com.nowait.domain.restaurant.type.DayOfWeek;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantHourRequest {
	
	@NotNull(message = "요일은 필수입니다.")
	private DayOfWeek dayOfWeek;
	
	private LocalTime openTime;
	
	private LocalTime closeTime;
	
	@NotNull(message = "정기 휴무 여부를 입력해주세요.")
	private String isRegularHoliday;
}
