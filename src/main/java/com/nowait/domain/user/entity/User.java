package com.nowait.domain.user.entity;

<<<<<<< HEAD
import org.springframework.data.annotation.Id;

import com.nowait.domain.user.type.UserRole;
import com.nowait.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity{
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, unique = true, length = 100)
	private String email;
	
	@Column(nullable = false, length = 255)
	private String password;
	
	@Column(nullable = false, length = 50)
	private String name;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;
	
	@Builder
	public User(String email, String password, String name, UserRole role) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.role = role;
	}
	
	public void updateProfile(String name) {
		if (name != null && !name.trim().isEmpty()) {
			this.name = name;
		}
	}
=======
public class User {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d

}
