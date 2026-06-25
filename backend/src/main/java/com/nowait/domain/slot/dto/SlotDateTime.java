// domain/slot/dto/SlotDateTime.java
package com.nowait.domain.slot.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/*
 * 예약 응답 조립 시 슬롯의 날짜/시간만 필요한 호출부(ReservationService.buildFromRedis)를 위한
 * 캐시 전용 경량 DTO. Slot 엔티티를 그대로 캐싱하면 지연 로딩되는 restaurant
 * 연관관계(Hibernate 프록시)가 함께 직렬화 대상에 포함되어 GenericJackson2JsonRedisSerializer가
 * "No serializer found for ... ByteBuddyInterceptor" 예외로 실패한다. 순수 값 객체만 캐싱한다.
 */
public record SlotDateTime(Long slotId, LocalDate slotDate, LocalTime slotTime) {
}
