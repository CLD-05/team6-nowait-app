package com.nowait.domain.auth.service;

import com.nowait.domain.auth.dto.LoginRequest;
import com.nowait.domain.auth.dto.OwnerSignUpRequest;
import com.nowait.domain.auth.dto.SignUpRequest;
import com.nowait.domain.auth.dto.TokenResponse;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.domain.user.type.UserRole;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import com.nowait.global.security.jwt.JwtTokenProvider;
import com.nowait.global.security.jwt.RefreshTokenStore;
import com.nowait.global.security.jwt.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenStore refreshTokenStore;
  private final TokenBlacklist tokenBlacklist;

  // 일반 회원가입. role = USER 고정.
  @Transactional
  public void signUp(SignUpRequest request) {
    validateEmailNotDuplicated(request.email());

    User user = User.builder()
        .email(request.email())
        .password(passwordEncoder.encode(request.password()))
        .name(request.name())
        .role(UserRole.USER)
        .build();

    userRepository.save(user);
    log.info("User signed up. userId={}, email={}", user.getId(), user.getEmail());
  }

  /**
   * 점주 회원가입. role = OWNER 고정.
   * TODO: restaurant 도메인 머지 후 식당 등록 + RestaurantOwner 연결 트랜잭션 추가
   */
  @Transactional
  public void signUpAsOwner(OwnerSignUpRequest request) {
    validateEmailNotDuplicated(request.email());

    User owner = User.builder()
        .email(request.email())
        .password(passwordEncoder.encode(request.password()))
        .name(request.name())
        .role(UserRole.OWNER)
        .build();

    userRepository.save(owner);
    log.info("Owner signed up. userId={}, email={}", owner.getId(), owner.getEmail());

    // TODO: 아래는 restaurant 도메인 머지 후 추가
    // Restaurant restaurant = restaurantService.create(request.restaurant());
    // restaurantOwnerService.link(owner, restaurant);
  }

  /**
   * 로그인. Access는 응답 body, Refresh는 LoginResult로 반환해 컨트롤러가 쿠키로 굽는다.
   */
  public LoginResult login(LoginRequest request) {
    User user = userRepository.findByEmailAndIsDeleted(request.email(), "N")
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    String accessToken = jwtTokenProvider.createAccessToken(
        user.getId(), user.getEmail(), user.getRole());
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

    refreshTokenStore.save(user.getId(), refreshToken,
        jwtTokenProvider.getRefreshTokenValiditySeconds());

    log.info("User logged in. userId={}", user.getId());

    TokenResponse body = TokenResponse.bearer(
        accessToken, user.getId(), user.getEmail(), user.getName(), user.getRole());
    return new LoginResult(body, refreshToken);
  }

  /**
   * Refresh Token Rotation.
   * 1) 토큰 자체 유효성 + type=REFRESH 검증
   * 2) Redis 저장값과 일치 (재사용 감지 — 다르면 강제 무효화)
   * 3) 새 Access + 새 Refresh 발급, Redis 덮어쓰기
   */
  public RefreshResult refresh(String refreshToken) {
    if (refreshToken == null || !jwtTokenProvider.validate(refreshToken)
        || !jwtTokenProvider.isRefreshToken(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }

    Long userId = jwtTokenProvider.getUserId(refreshToken);

    if (!refreshTokenStore.matches(userId, refreshToken)) {
      // 재사용 감지 — 잠재적 탈취. 해당 사용자의 refresh를 통째로 날려 강제 로그아웃 효과.
      refreshTokenStore.delete(userId);
      log.warn("Refresh token reuse detected. userId={}", userId);
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }

    User user = userRepository.findById(userId)
        .filter(u -> "N".equals(u.getIsDeleted()))
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_WITHDRAWN));

    String newAccess = jwtTokenProvider.createAccessToken(
        user.getId(), user.getEmail(), user.getRole());
    String newRefresh = jwtTokenProvider.createRefreshToken(user.getId());
    refreshTokenStore.save(user.getId(), newRefresh,
        jwtTokenProvider.getRefreshTokenValiditySeconds());

    return new RefreshResult(newAccess, newRefresh);
  }

  /**
   * Access는 블랙리스트로 즉시 무효화. Refresh는 Redis에서 삭제.
   * 둘 다 best-effort — 토큰이 깨졌어도 로그아웃 자체는 성공으로 처리.
   */
  public void logout(String accessToken, String refreshToken) {
    if (accessToken != null && jwtTokenProvider.validate(accessToken)
        && jwtTokenProvider.isAccessToken(accessToken)) {
      String jti = jwtTokenProvider.getJti(accessToken);
      Date exp = jwtTokenProvider.getExpiration(accessToken);
      long ttl = (exp.getTime() - System.currentTimeMillis()) / 1000;
      tokenBlacklist.add(jti, ttl);
    }

    if (refreshToken != null && jwtTokenProvider.validate(refreshToken)
        && jwtTokenProvider.isRefreshToken(refreshToken)) {
      refreshTokenStore.delete(jwtTokenProvider.getUserId(refreshToken));
    }
  }

  private void validateEmailNotDuplicated(String email) {
    if (userRepository.existsByEmailAndIsDeleted(email, "N")) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }
  }

  public record LoginResult(TokenResponse body, String refreshToken) {}

  public record RefreshResult(String accessToken, String refreshToken) {}
}
