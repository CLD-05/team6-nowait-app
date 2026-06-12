package com.nowait.global.security.jwt;

import com.nowait.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && tokenProvider.validate(token)) {
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
