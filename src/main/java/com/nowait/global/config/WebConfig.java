package com.nowait.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * CORS는 SecurityConfig.corsConfigurationSource()에서 단일하게 관리한다.
 * 이전에는 이 클래스가 allowedHeaders("*")로 별도의 MVC CORS 매핑을 추가해
 * SecurityConfig의 명시적 헤더 화이트리스트(Authorization, Content-Type)와
 * 충돌/불일치할 수 있었다. 두 군데에서 따로 CORS를 선언하지 않도록 여기서는 제거한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
