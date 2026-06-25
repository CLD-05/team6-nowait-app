package com.nowait.domain.favorite.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.favorite.dto.FavoriteResponse;
import com.nowait.domain.favorite.entity.Favorite;
import com.nowait.domain.favorite.repository.FavoriteRepository;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.user.entity.User;
import com.nowait.domain.user.repository.UserRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import com.nowait.global.s3.S3Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {
	
	private final FavoriteRepository favoriteRepository;
	private final UserRepository userRepository;
	private final RestaurantRepository restaurantRepository;
	private final S3Service s3Service;
	
	/**
     * ⭐ [핵심 기능 1] 즐겨찾기 추가
     */
	@Transactional
	public String toggleFavorite(Long userId, Long restaurantId) {
		// 1. 창고에서 유저와 식당이 진짜 존재하는지 먼저 검사합니다.
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		
		// 2. 이 유저가 이 식당을 이미 찜했는지 창고에서 꺼내봅니다.
		Optional<Favorite> optionalFavorite = favoriteRepository.findByUserAndRestaurant(user, restaurant);
		
		if (optionalFavorite.isPresent()) {
			// 3-1. 이미 존재한다면? 즐겨찾기 취소(삭제)!
			favoriteRepository.delete(optionalFavorite.get());
			return "즐겨찾기가 취소되었습니다.";
		} else {
			// 3-2. 없다면? 새롭게 상자를 조립해서 저장(등록)!
			Favorite favorite = Favorite.builder()
					.user(user)
					.restaurant(restaurant)
					.build();
			favoriteRepository.save(favorite);
			return "즐겨찾기에 등록되었습니다.";
		}
	}
	
	/**
     * ⭐ [핵심 기능 2] 내가 찜한 식당 목록 조회
     */
	public List<FavoriteResponse> getMyFavorites(Long userId) {
		
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		
		List<Favorite> favorites = favoriteRepository.findAllByUser(user);
		
		return favorites.stream()
				.map(f -> new FavoriteResponse(
					f,
					s3Service.resolveReadableImageUrl(f.getRestaurant().getImageUrl())
				))
				.collect(Collectors.toList());
	}

}
