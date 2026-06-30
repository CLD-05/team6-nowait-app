package com.nowait.global.security.jwt;

import com.nowait.domain.user.type.UserRole;
import com.nowait.global.security.principal.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist tokenBlacklist;
    private final WithdrawnUserCache withdrawnUserCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null
                && tokenProvider.validate(token)
                && tokenProvider.isAccessToken(token)
                && !tokenBlacklist.contains(tokenProvider.getJti(token))) {
            Long userId = tokenProvider.getUserId(token);

            // 탈퇴/차단된 계정은 토큰이 유효해도 인증 거부.
            // 매 요청 DB 조회(userRepository.findById) 대신 Redis 마커로 판단해
            // 상태조회 polling 트래픽이 DB 커넥션 부하로 전환되지 않게 한다.
            if (!withdrawnUserCache.isWithdrawn(userId)) {
                String email = tokenProvider.getEmail(token);
                UserRole role = tokenProvider.getRole(token);

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
