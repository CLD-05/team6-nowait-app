package com.nowait.domain.auth.dto;

<<<<<<< HEAD
public class TokenResponse {

}
=======
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
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
