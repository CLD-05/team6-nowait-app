package com.nowait.global.security.jwt;

<<<<<<< HEAD
public class JwtAuthenticationFilter {

}
=======
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && tokenProvider.validate(token)) {
            Long userId = tokenProvider.getUserId(token);
            String email = tokenProvider.getEmail(token);
            UserRole role = tokenProvider.getRole(token);

            CustomUserDetails principal = CustomUserDetails.fromToken(userId, email, role);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(auth);
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
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
