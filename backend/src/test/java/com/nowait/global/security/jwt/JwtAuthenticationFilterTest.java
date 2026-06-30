package com.nowait.global.security.jwt;

import com.nowait.domain.user.type.UserRole;
import com.nowait.global.security.principal.CustomUserDetails;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 인증 필터가 매 요청마다 DB(UserRepository)를 조회하지 않고
 * JWT claim 기반으로 인증하며, 탈퇴 차단은 Redis 마커로 처리하는지 검증한다.
 *
 * (필터에서 UserRepository 의존 자체를 제거했으므로 "매 요청 DB 조회 없음"은
 *  구조적으로 보장된다 — 이 테스트는 claim 기반 인증/탈퇴 차단 동작을 고정한다.)
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  private static final String TOKEN = "valid.token";

  @Mock JwtTokenProvider tokenProvider;
  @Mock TokenBlacklist tokenBlacklist;
  @Mock WithdrawnUserCache withdrawnUserCache;

  @InjectMocks JwtAuthenticationFilter filter;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private MockHttpServletRequest authedRequest() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + TOKEN);
    return req;
  }

  @Test
  @DisplayName("유효한 access token이면 DB 조회 없이 claim 기반으로 인증한다")
  void authenticatesFromClaimsWithoutDb() throws Exception {
    when(tokenProvider.validate(TOKEN)).thenReturn(true);
    when(tokenProvider.isAccessToken(TOKEN)).thenReturn(true);
    when(tokenProvider.getJti(TOKEN)).thenReturn("jti-1");
    when(tokenBlacklist.contains("jti-1")).thenReturn(false);
    when(tokenProvider.getUserId(TOKEN)).thenReturn(7L);
    when(withdrawnUserCache.isWithdrawn(7L)).thenReturn(false);
    when(tokenProvider.getEmail(TOKEN)).thenReturn("u@nowait.com");
    when(tokenProvider.getRole(TOKEN)).thenReturn(UserRole.USER);

    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest req = authedRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isInstanceOf(CustomUserDetails.class);
    assertThat(((CustomUserDetails) auth.getPrincipal()).getUserId()).isEqualTo(7L);
    verify(chain).doFilter(req, res);
  }

  @Test
  @DisplayName("탈퇴 마커가 있으면 토큰이 유효해도 인증하지 않는다")
  void rejectsWithdrawnUser() throws Exception {
    when(tokenProvider.validate(TOKEN)).thenReturn(true);
    when(tokenProvider.isAccessToken(TOKEN)).thenReturn(true);
    when(tokenProvider.getJti(TOKEN)).thenReturn("jti-1");
    when(tokenBlacklist.contains("jti-1")).thenReturn(false);
    when(tokenProvider.getUserId(TOKEN)).thenReturn(7L);
    when(withdrawnUserCache.isWithdrawn(7L)).thenReturn(true);

    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest req = authedRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(req, res);
    // 차단은 Redis 마커만으로 판단 — claim(email/role)까지 진행하지 않는다
    verify(tokenProvider, never()).getEmail(anyString());
    verify(tokenProvider, never()).getRole(anyString());
  }

  @Test
  @DisplayName("토큰이 없으면 인증 없이 체인을 통과시킨다")
  void passesThroughWithoutToken() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(req, res);
  }
}
