package com.nowait.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
   Spring 스케줄링 활성화
   - 웨이팅 호출 타임아웃 자동 취소 등
*/
@Configuration
@EnableScheduling
public class SchedulingConfig {
}