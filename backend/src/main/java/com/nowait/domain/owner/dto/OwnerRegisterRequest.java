package com.nowait.domain.owner.dto;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerRegisterRequest {
	
	@NotNull(message = "점주(유저) ID는 필수입니다.")
	private Long userId;
	
	@NotNull(message = "담당할 식당 ID는 필수입니다.")
	private Long restaurantId;
	
	@Builder
	public OwnerRegisterRequest(Long userId, Long restaurantId) {
		this.userId = userId;
		this.restaurantId = restaurantId;
	}

}
