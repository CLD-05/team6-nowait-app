package com.nowait.domain.auth.controller;

import com.nowait.domain.auth.dto.LoginRequest;
import com.nowait.domain.auth.dto.OwnerSignUpRequest;
import com.nowait.domain.auth.dto.SignUpRequest;
import com.nowait.domain.auth.dto.TokenResponse;
import com.nowait.domain.auth.service.AuthService;
import com.nowait.domain.auth.service.AuthService.LoginResult;
import com.nowait.domain.auth.service.AuthService.RefreshResult;
import com.nowait.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private static final String REFRESH_COOKIE = "refreshToken";
  private static final String COOKIE_PATH = "/api/v1/auth";

  private final AuthService authService;
  private final JwtTokenProvider jwtTokenProvider;

  @Value("${cookie.secure}")
  private boolean cookieSecure;

  @Value("${cookie.same-site}")
  private String cookieSameSite;

  @PostMapping("/signup")
  public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequest request) {
    authService.signUp(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/signup/owner")
  public ResponseEntity<Void> signUpAsOwner(@Valid @RequestBody OwnerSignUpRequest request) {
    authService.signUpAsOwner(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = authService.login(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken(),
            jwtTokenProvider.getRefreshTokenValiditySeconds()).toString())
        .body(result.body());
  }

  /**
   * Refresh Rotation. 쿠키의 refreshToken을 읽어 새 Access + 새 Refresh 발급.
   * 새 Refresh는 Set-Cookie로 덮어쓰고, 새 Access는 body로 반환.
   */
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
    String refreshToken = readRefreshCookie(request);
    RefreshResult result = authService.refresh(refreshToken);

    Long userId = jwtTokenProvider.getUserId(result.accessToken());
    String email = jwtTokenProvider.getEmail(result.accessToken());

    // 이름은 토큰에 안 들어가 있어 body 채우려면 추가 조회가 필요 — 일단 최소 정보만 반환.
    TokenResponse body = TokenResponse.bearer(
        result.accessToken(), userId, email, null, jwtTokenProvider.getRole(result.accessToken()));

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken(),
            jwtTokenProvider.getRefreshTokenValiditySeconds()).toString())
        .body(body);
  }

  /**
   * 로그아웃. Access(헤더)는 블랙리스트, Refresh(쿠키)는 Redis 삭제 + 쿠키 만료.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      HttpServletRequest request) {
    String accessToken = stripBearer(authorization);
    String refreshToken = readRefreshCookie(request);

    authService.logout(accessToken, refreshToken);

    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie("", 0).toString())
        .build();
  }

  private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(cookieSameSite)
        .path(COOKIE_PATH)
        .maxAge(maxAgeSeconds)
        .build();
  }

  private String readRefreshCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if (REFRESH_COOKIE.equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }

  private String stripBearer(String authorization) {
    if (authorization == null) return null;
    String prefix = "Bearer ";
    if (authorization.startsWith(prefix)) {
      return authorization.substring(prefix.length());
    }
    return null;
  }
}
