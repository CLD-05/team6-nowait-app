package com.nowait.domain.user.service;

import java.util.List;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
	
	private final UserRepository userRepository;
	private final RestaurantRepository restaurantRepository; // 💡 식당 연쇄 삭제를 위해 주입!
	
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
    public void withdraw(Long userId) {
        // 1. 회원 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 회원 소프트 딜리트
        user.withdrawUser();

        // 3. 🌟 [연쇄 반응] 이 사람이 사장님(점주)이라면, 운영 중인 모든 식당도 강제 소프트 딜리트!
        List<Restaurant> myRestaurants = restaurantRepository.findByOwnerIdAndIsDeleted(userId, "N");
        for (Restaurant restaurant : myRestaurants) {
            restaurant.deleteRestaurant(); // 식당도 전부 'Y' 및 PERMANENTLY_CLOSED로 변경!
        }
    }
}
