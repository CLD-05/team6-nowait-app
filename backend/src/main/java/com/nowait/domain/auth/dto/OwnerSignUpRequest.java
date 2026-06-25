package com.nowait.domain.auth.dto;

import com.nowait.domain.restaurant.type.RestaurantCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record OwnerSignUpRequest(

    @NotBlank(message = "이메일은 필수입니다.") @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 100, message = "이메일은 100자 이하여야 합니다.") String email,

    @NotBlank(message = "비밀번호는 필수입니다.") @Size(min = 8, max = 255, message = "비밀번호는 8자 이상 255자 이하여야 합니다.") String password,

    @NotBlank(message = "이름은 필수입니다.") @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name,

    RestaurantInfo restaurant) {
  public record RestaurantInfo(

      @NotBlank(message = "상호명은 필수입니다.") @Size(max = 100, message = "상호명은 100자 이하여야 합니다.") String restaurantName,

      @NotNull(message = "카테고리는 필수입니다.") RestaurantCategory category,

      @NotBlank(message = "주소는 필수입니다.") @Size(max = 255, message = "주소는 255자 이하여야 합니다.") String address,

      @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.") String phoneNumber,

      String description,

      @Size(max = 255, message = "메인 메뉴 이름은 255자 이하여야 합니다.") String mainMenuName,

      @NotNull(message = "오픈 시간은 필수입니다.") LocalTime openTime,

      @NotNull(message = "마감 시간은 필수입니다.") LocalTime closeTime,

      @Size(max = 100, message = "휴무일은 100자 이하여야 합니다.") String closedDays,

      boolean parkingAvailable,
      boolean wifiAvailable,
      boolean multilingualMenuAvailable) {
  }
}
