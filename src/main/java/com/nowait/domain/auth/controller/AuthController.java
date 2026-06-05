package com.nowait.domain.auth.controller;

import com.nowait.domain.auth.dto.LoginRequest;
import com.nowait.domain.auth.dto.OwnerSignUpRequest;
import com.nowait.domain.auth.dto.SignUpRequest;
import com.nowait.domain.auth.dto.TokenResponse;
import com.nowait.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

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
  public ResponseEntity<Void> logout() {
    // 현재는 stateless JWT라 서버 측 처리 없음 (클라이언트가 토큰 삭제)
    // Redis 도입 후 토큰 블랙리스트 처리 예정
    return ResponseEntity.ok().build();
  }
}