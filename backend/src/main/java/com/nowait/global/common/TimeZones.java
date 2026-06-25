package com.nowait.global.common;

import java.time.ZoneId;

/**
 * 서비스 전역 타임존 상수.
 * EKS Pod 가 기본 UTC 로 동작하더라도 비즈니스 시각 비교(영업시간 등)는 KST 로 일관 처리한다.
 * {@code LocalTime.now()} / {@code LocalDateTime.now()} / {@code LocalDate.now()} 호출 시
 * 반드시 {@link #KST} 를 함께 넘겨 사용한다.
 */
public final class TimeZones {

  public static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private TimeZones() {}
}
