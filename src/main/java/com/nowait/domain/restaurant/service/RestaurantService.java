package com.nowait.domain.restaurant.service;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.nowait.domain.restaurant.type.DayOfWeek;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.restaurant.dto.RestaurantDetailResponse;
import com.nowait.domain.restaurant.dto.RestaurantHourRequest;
import com.nowait.domain.restaurant.dto.RestaurantListResponse;
import com.nowait.domain.restaurant.dto.RestaurantRegisterRequest;
import com.nowait.domain.restaurant.dto.RestaurantUpdateRequest;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.type.RestaurantCategory;
import com.nowait.domain.restaurant.type.RestaurantStatus;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {
	
	private final RestaurantRepository restaurantRepository;
	private final RestaurantHourRepository restaurantHourRepository;
	
	@Transactional
    public Long registerRestaurant(RestaurantRegisterRequest request, Long ownerId) {
		if (restaurantRepository.existsByOwnerIdAndIsDeleted(ownerId, "N")) {
			throw new BusinessException(ErrorCode.OWNER_ALREADY_EXISTS);
		}

        // 1. 기존 로직: 식당 정보를 먼저 저장합니다.
        Restaurant restaurant = request.toEntity(ownerId);
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        // 요청에 포함된 영업시간 저장
        List<RestaurantHourRequest> hourRequests = request.getRestaurantHours() == null
            ? List.of() : request.getRestaurantHours();

        Set<DayOfWeek> providedDays = hourRequests.stream()
            .map(RestaurantHourRequest::getDayOfWeek)
            .collect(Collectors.toSet());

        for (RestaurantHourRequest hourReq : hourRequests) {
            boolean isHoliday = "Y".equals(hourReq.getIsRegularHoliday());
            LocalTime finalOpenTime = isHoliday ? LocalTime.MIDNIGHT : hourReq.getOpenTime();
            LocalTime finalCloseTime = isHoliday ? LocalTime.MIDNIGHT : hourReq.getCloseTime();
            restaurantHourRepository.save(RestaurantHour.builder()
                .restaurant(savedRestaurant)
                .dayOfWeek(hourReq.getDayOfWeek())
                .openTime(finalOpenTime)
                .closeTime(finalCloseTime)
                .isRegularHoliday(hourReq.getIsRegularHoliday())
                .build());
        }

        // 누락된 요일은 기본값(11:00~22:00, 정기휴무 없음)으로 자동 생성
        Arrays.stream(DayOfWeek.values())
            .filter(day -> !providedDays.contains(day))
            .forEach(day -> restaurantHourRepository.save(RestaurantHour.builder()
                .restaurant(savedRestaurant)
                .dayOfWeek(day)
                .openTime(LocalTime.of(11, 0))
                .closeTime(LocalTime.of(22, 0))
                .isRegularHoliday("N")
                .build()));

        return savedRestaurant.getId();
    }
	
	public List<RestaurantListResponse> getAllRestaurants() {
		return restaurantRepository.findAll().stream()
				.map(RestaurantListResponse::from)
				.collect(Collectors.toList());
	}
	
	public List<RestaurantListResponse> searchRestaurantsByName(String keyword) {
	    return restaurantRepository.findByNameContaining(keyword).stream()
	            .map(RestaurantListResponse::from)
	            .collect(Collectors.toList());
	}
	
	public List<RestaurantListResponse> getRestaurantsByCategory(RestaurantCategory category) {
		return restaurantRepository.findByCategory(category).stream()
				.map(RestaurantListResponse::from)
				.collect(Collectors.toList());
	}
	
	public RestaurantDetailResponse getRestaurantDetail(Long restaurantId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		return RestaurantDetailResponse.from(restaurant);
	}

	public RestaurantDetailResponse getMyRestaurant(Long ownerId) {
		Restaurant restaurant = restaurantRepository.findFirstByOwnerIdAndIsDeleted(ownerId, "N")
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		return RestaurantDetailResponse.from(restaurant);
	}
	
	@Transactional
	public void updateRestaurant(Long restaurantId, RestaurantUpdateRequest request, Long ownerId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		
		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}

		restaurant.updateDetails(
				request.getName(), request.getCategory(), request.getAddress(), request.getPhoneNumber(),
				request.getDescription(), request.getImageUrl(), request.getMainMenuName(),
				request.getParkingAvailable(), request.getWifiAvailable(), request.getMultilingualMenuAvailable(),
				request.getStatus(), request.getReservationAvailable(), request.getWaitingAvailable());
	}
	
	@Transactional
	public void updateStatus(Long restaurantId, RestaurantStatus status, Long ownerId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}
		restaurant.updateStatus(status);
	}

	@Transactional
	public void deleteRestaurant(Long restaurantId, Long ownerId) {
		
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		
		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}
		// [주의] 지금은 아직 MySQL에 칼럼을 안 만들었으니, 
        // 나중에 팀원들과 이야기해서 엔티티에 필드 추가하면 아래 주석을 풀어줄 것입니다!
         restaurant.deleteRestaurant();
	}
	
	
	@Transactional
    public void updateRestaurantImage(Long restaurantId, Long ownerId, String imageUrl) {
        // 식당이 존재하는지 확인
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }

        // 엔티티의 메서드를 호출해서 새 이미지 URL로 갈아끼웁니다 (더티 체킹으로 자동 저장)
        restaurant.updateImage(imageUrl);
    }
	
    public void verifyOwner(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
    }

	@Transactional
    public void deleteRestaurantImage(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }

        // 이미지 주소를 완전히 비워줍니다 (null)
        restaurant.updateImage(null);
    }
}
