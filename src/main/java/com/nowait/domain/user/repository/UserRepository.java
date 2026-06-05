package com.nowait.domain.user.repository;

<<<<<<< HEAD
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nowait.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long>{
	
	Optional<User> findByEmail(String email);
	
	boolean existsByEmail(String email);
=======
public interface UserRepository {

>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
}
