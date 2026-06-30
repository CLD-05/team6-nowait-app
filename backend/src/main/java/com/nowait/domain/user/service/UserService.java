package com.nowait.domain.user.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.user.dto.UserResponse;
import com.nowait.domain.user.dto.UserUpdateRequest;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import com.nowait.global.security.jwt.WithdrawnUserCache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final RestaurantRepository restaurantRepository; // 💡 식당 연쇄 삭제를 위해 주입!
	private final WithdrawnUserCache withdrawnUserCache; // 탈퇴 즉시 발급된 토큰 무력화 (인증 필터에서 DB 조회 제거)
	
	/*
	 * @param
	 * 
	 * @return
	 */
	public UserResponse getUserInfo(Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. ID: " + id));
		
		return UserResponse.of(user);
	}
	
	@Transactional
	public UserResponse updateUserInfo(Long id, UserUpdateRequest request) {
		
		User user = userRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. ID: " + id));
		
		user.updateProfile(request.getName());
		
		return UserResponse.of(user);
	}
	
	@Transactional
    @Caching(evict = {
        // 💥 탈퇴하는 유저가 점주일 경우를 대비해, 식당 상세 정보 캐시 전체를 안전하게 리셋합니다.
        @CacheEvict(value = "restaurant", allEntries = true, cacheManager = "cacheManager"),
        // 💥 해당 점주의 식당 영업시간 캐시 전체도 안전하게 함께 리셋합니다.
        @CacheEvict(value = "restaurant_hours", allEntries = true, cacheManager = "cacheManager")
    })
    public void withdraw(Long userId) {
        // 1. 회원 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 회원 소프트 딜리트 + 이메일 익명화 (unique 제약 해제 → 재가입 허용)
        user.withdrawUser();
        userRepository.save(user);
        userRepository.flush();

        // 2-1. 탈퇴 마커 등록 — 인증 필터가 매 요청 DB 조회 없이도 기존 Access 토큰을 거부하도록.
        //      (필터에서 userRepository.findById 기반 탈퇴 검사를 제거했으므로 반드시 필요)
        withdrawnUserCache.markWithdrawn(userId);

        // 3. 🌟 [연쇄 반응] 이 사람이 사장님(점주)이라면, 운영 중인 모든 식당도 강제 소프트 딜리트!
        List<Restaurant> myRestaurants = restaurantRepository.findByOwnerIdAndIsDeleted(userId, "N");
        for (Restaurant restaurant : myRestaurants) {
            restaurant.deleteRestaurant(); // 식당도 전부 'Y' 및 PERMANENTLY_CLOSED로 변경!
        }
    }
}
