package com.nowait.domain.user.dto;

<<<<<<< HEAD
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.type.UserRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
	
	private Long id;
	private String email;
	private String name;
	private UserRole role;
	
	public static UserResponse of(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getRole()
				);
	}
=======
public class UserResponse {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
