package com.nowait.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * JPA Auditing 활성화 — BaseTimeEntity 의
 * @CreatedDate / @LastModifiedDate 가 INSERT/UPDATE 시점에 자동 채워지도록 함.
 *
 * 이게 없으면 created_at/updated_at 이 null 로 들어가 NOT NULL 제약 위반.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
