package com.nowait.domain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nowait.domain.auth.dto.LoginRequest;
import com.nowait.domain.auth.dto.OwnerSignUpRequest;
import com.nowait.domain.auth.dto.SignUpRequest;
import com.nowait.domain.auth.dto.TokenResponse;
import com.nowait.domain.auth.service.AuthService;
import com.nowait.global.security.jwt.JwtBlacklistService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final JwtBlacklistService jwtBlacklistService;

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
    TokenResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
	  
	// 1. HTTP 요청 헤더(Authorization)에서 "Bearer 토큰값"을 추출합니다.
	  String token = resolveTokenFromHeader(request);
	  
	// 2. 토큰이 존재한다면 Redis 블랙리스트 장부에 등록하여 영구 파기합니다.
	  if (token != null) {
		  jwtBlacklistService.registerBlacklist(token);
	  }
	  
	// 3. 로그아웃 성공 반환 (200 OK)
	  return ResponseEntity.ok().build();
  }
  
  /**
   * 🔍 HTTP 요청 헤더에서 순수 JWT 토큰 문자열만 파싱하는 헬퍼 메서드
   */
  private String resolveTokenFromHeader(HttpServletRequest request) {
      String bearerToken = request.getHeader("Authorization");
      if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
          return bearerToken.substring(7); // "Bearer " 가 7글자이므로 이후의 토큰값만 잘라냅니다.
      }
      return null;
  }
}