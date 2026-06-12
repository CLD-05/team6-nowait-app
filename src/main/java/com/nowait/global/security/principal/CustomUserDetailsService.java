package com.nowait.global.security.principal;

import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndIsDeleted(email, "N")
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole()
        );
    }
}
