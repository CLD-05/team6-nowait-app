package com.nowait.domain.restaurant.service;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.nowait.domain.restaurant.type.DayOfWeek;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
import com.nowait.global.s3.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {
	
	private final RestaurantRepository restaurantRepository;
	private final RestaurantHourRepository restaurantHourRepository;
	private final S3Service s3Service;
	
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
            .map(restaurant -> RestaurantListResponse.from(
                restaurant,
                s3Service.resolveReadableImageUrl(restaurant.getImageUrl())
            ))
            .collect(Collectors.toList());
	}
	
	public List<RestaurantListResponse> searchRestaurantsByName(String keyword) {
    return restaurantRepository.findByNameContaining(keyword).stream()
            .map(restaurant -> RestaurantListResponse.from(
                restaurant,
                s3Service.resolveReadableImageUrl(restaurant.getImageUrl())
            ))
            .collect(Collectors.toList());
	}

	public List<RestaurantListResponse> getRestaurantsByCategory(RestaurantCategory category) {
		return restaurantRepository.findByCategory(category).stream()
				.map(restaurant -> RestaurantListResponse.from(
					restaurant,
					s3Service.resolveReadableImageUrl(restaurant.getImageUrl())
				))
				.collect(Collectors.toList());
	}
	
	/**
     * 🏪 식당 상세 조회 (캐싱 적용)
     * - 캐시에 데이터가 있으면 MySQL을 건드리지 않고 Redis에서 바로 반환합니다.
     * - 캐시에 없으면(Cache Miss) 원래대로 MySQL에서 조회한 후, Redis에 저장하고 반환합니다.
     */
	@Cacheable(value = "restaurant", key = "#restaurantId", cacheManager = "cacheManager")
	public RestaurantDetailResponse getRestaurantDetail(Long restaurantId) {
		log.info("🔍 [MySQL 조회] Redis에 없어서 DB에서 식당 정보를 꺼내옵니다. ID: {}", restaurantId);
		
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

		return RestaurantDetailResponse.from(
			restaurant,
			s3Service.resolveReadableImageUrl(restaurant.getImageUrl())
		);
	}

	public RestaurantDetailResponse getMyRestaurant(Long ownerId) {
		Restaurant restaurant = restaurantRepository.findFirstByOwnerIdAndIsDeleted(ownerId, "N")
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

		return RestaurantDetailResponse.from(
			restaurant,
			s3Service.resolveReadableImageUrl(restaurant.getImageUrl())
		);
	}
	
	@Transactional
	@CacheEvict(value = "restaurant", key = "#restaurantId", cacheManager = "cacheManager")
	public void updateRestaurant(Long restaurantId, RestaurantUpdateRequest request, Long ownerId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}

		// presigned URL이 들어오더라도 imageKey만 저장한다. (DB image_url 컬럼 length=500 초과 방지)
		String normalizedImageUrl = normalizeImageUrl(request.getImageUrl());

		restaurant.updateDetails(
				request.getName(), request.getCategory(), request.getAddress(), request.getPhoneNumber(),
				request.getDescription(), normalizedImageUrl, request.getMainMenuName(),
				request.getParkingAvailable(), request.getWifiAvailable(), request.getMultilingualMenuAvailable(),
				request.getStatus(), request.getReservationAvailable(), request.getWaitingAvailable());
		
		log.info("[Cache Evict] 식당 정보가 수정되어 Redis 캐시를 삭제했습니다. ID: {}", restaurantId);
	}

	/**
	 * 프론트가 어떤 형태(imageKey / static 경로 / S3 직접 URL / presigned GET URL)를 보내도
	 * DB에는 항상 짧은 imageKey 형태로만 저장하도록 정규화한다.
	 * 기존 정책: imageKey = "restaurants/{id}/{uuid}.{ext}" 또는 "/images/..." (seed).
	 */
	private String normalizeImageUrl(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return imageUrl;
		}
		if (imageUrl.startsWith("/images/") || imageUrl.startsWith("restaurants/")) {
			return imageUrl;
		}
		int amazonIdx = imageUrl.indexOf(".amazonaws.com/");
		if (amazonIdx > 0) {
			String afterDomain = imageUrl.substring(amazonIdx + ".amazonaws.com/".length());
			int q = afterDomain.indexOf('?');
			return q > 0 ? afterDomain.substring(0, q) : afterDomain;
		}
		return imageUrl;
	}
	
	@Transactional
	@CacheEvict(value = "restaurant", key = "#restaurantId", cacheManager = "cacheManager")
	public void updateStatus(Long restaurantId, RestaurantStatus status, Long ownerId) {
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}
		restaurant.updateStatus(status);
		
		log.info("💥 [Cache Evict] 식당 상태가 변경되어 Redis 캐시를 삭제했습니다. ID: {}", restaurantId);
	}

	@Transactional
	@CacheEvict(value = "restaurant", key = "#restaurantId", cacheManager = "cacheManager")
	public void deleteRestaurant(Long restaurantId, Long ownerId) {
		
		Restaurant restaurant = restaurantRepository.findById(restaurantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
		
		if (!restaurant.getOwnerId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
		}
		// [주의] 지금은 아직 MySQL에 칼럼을 안 만들었으니, 
        // 나중에 팀원들과 이야기해서 엔티티에 필드 추가하면 아래 주석을 풀어줄 것입니다!
         restaurant.deleteRestaurant();
         
         log.info("💥 [Cache Evict] 식당이 삭제되어 Redis 캐시를 파기했습니다. ID: {}", restaurantId);
	}
	
	
	@Transactional
	@CacheEvict(value = "restaurant", key = "#restaurantId", cacheManager = "cacheManager")
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
	@CacheEvict(value = "restaurant", key = "#restaurantId", cacheManager = "cacheManager")
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
