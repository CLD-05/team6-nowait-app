package com.nowait.domain.user.service;

<<<<<<< HEAD
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.user.dto.UserResponse;
import com.nowait.domain.user.dto.UserUpdateRequest;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
	
	private final UserRepository userRepository;
	
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
=======
public class UserService {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
