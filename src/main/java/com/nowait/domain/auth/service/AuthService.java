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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

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

  // 로그인. 이메일+비밀번호 검증 후 JWT 발급.

  public TokenResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    String accessToken = jwtTokenProvider.createToken(
        user.getId(), user.getEmail(), user.getRole());

    log.info("User logged in. userId={}", user.getId());

    return TokenResponse.bearer(accessToken, user.getId(), user.getName(), user.getRole());
  }

  private void validateEmailNotDuplicated(String email) {
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }
  }
}