package com.nowait.global.security.jwt;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nowait.domain.user.repository.UserRepository;
import com.nowait.domain.user.type.UserRole;
import com.nowait.global.security.principal.CustomUserDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final JwtBlacklistService jwtBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && tokenProvider.validate(token)) {
        	
        	// 🔥 [고도화 방어벽 1차] 로그아웃된 토큰인지 Redis 장부(Blacklist)를 먼저 확인합니다.
            // 무거운 MySQL DB 조회가 일어나기 전에 초고속 메모리 단에서 먼저 해커를 차단합니다.
            if (jwtBlacklistService.isBlacklisted(token)) {
            	log.warn("[SECURITY WARNING] 로그아웃된 JWT 토큰으로 접근 시도 차단: {}...", token.substring(0, Math.min(token.length(), 15)));
                
                // 401 Unauthorized 에러와 함께 메시지를 즉시 반환하고 필터 체인을 즉시 종료(return)합니다.
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그아웃된 토큰입니다. 다시 로그인해주세요.");
                return; 
            }
            
            Long userId = tokenProvider.getUserId(token);
            String email = tokenProvider.getEmail(token);
            UserRole role = tokenProvider.getRole(token);

            // 탈퇴한 계정은 기존 토큰으로도 인증 거부
            boolean withdrawn = userRepository.findById(userId)
                    .map(u -> "Y".equals(u.getIsDeleted()))
                    .orElse(true);

            if (!withdrawn) {
                CustomUserDetails principal = CustomUserDetails.fromToken(userId, email, role);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HEADER);
        if (StringUtils.hasText(bearer) && bearer.startsWith(PREFIX)) {
            return bearer.substring(PREFIX.length());
        }
        return null;
    }
}
