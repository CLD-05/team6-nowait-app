package com.nowait.domain.auth.dto;

import com.nowait.domain.user.type.UserRole;

public record TokenResponse(
    String tokenType,
    String accessToken,
    Long userId,
    String name,
    UserRole role) {
  public static TokenResponse bearer(String accessToken, Long userId, String name, UserRole role) {
    return new TokenResponse("Bearer", accessToken, userId, name, role);
  }
}
