package com.nowait.global.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDatabaseCleanUpService {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private final StringRedisTemplate redisTemplate;
	private final List<String> tableNames = new ArrayList<>();
	
	// 1. 서버가 켜질 때 @Entity가 붙은 모든 테이블 이름을 자동으로 수집합니다.
	@PostConstruct
	public void init() {
		entityManager.getMetamodel().getEntities().stream()
					.filter(e -> e.getJavaType().getAnnotation(Entity.class) != null)
					.forEach(e -> {
						Table table =e.getJavaType().getAnnotation(Table.class);
						if (table != null && !table.name().isEmpty()) {
							tableNames.add(table.name());
						} else {
							// @Table 어노테이션이 없거나 이름이 비어있으면 엔티티 클래스 이름을 스네이크 케이스로 변환
							tableNames.add(convertToSnakeCase(e.getName()));
						}
					});
	}
	
	// 2. MySQL 테이블과 Redis 키를 통째로 초기화하는 메인 실행 메서드
	@Transactional
	public void execute() {
		log.info("[k6 Test] 데이터베이스 및 캐시 통합 초기화를 시작합니다.");
		
		// --------- [MySQL 초기화] ---------
        // 외래키(FK) 제약조건을 잠시 끄고 모든 테이블을 TRUNCATE(초기화)합니다.
		entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
		
		for (String tableName : tableNames) {
			// 🔒 [Flyway 및 필수 데이터 보호 장치]
	        // flyway_schema_history: Flyway가 관리하는 핵심 기록 테이블 (절대 지우면 안 됨)
	        // restaurants, menus: 유지하고 싶은 기초 더미데이터 테이블 이름 (우리 프로젝트에 맞게 수정)
	        if (tableName.equalsIgnoreCase("flyway_schema_history") ||
	                tableName.equalsIgnoreCase("users") ||
	                tableName.equalsIgnoreCase("restaurant") ||
	                tableName.equalsIgnoreCase("restaurant_hours") || 
	                tableName.equalsIgnoreCase("restaurant_owners") || 
	                tableName.equalsIgnoreCase("slots") || 
	                tableName.equalsIgnoreCase("favorite")) {
	                
	                log.info("ℹ️ Flyway 정적 마스터 데이터 보존: {}", tableName);
	            continue; // 지우지 않고 그냥 패스(건너뛰기)합니다!
	        }
	        
	        // 보호 대상이 아닌 데이터(예: waiting_history 등)만 골라서 비웁니다.
			entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
			// AUTO_INCREMENT 카운터도 1로 초기화해 줍니다.
			entityManager.createNativeQuery("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1").executeUpdate();
		}
		
		entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        log.info("✅ MySQL 모든 테이블 초기화 완료 (총 {}개)", tableNames.size());
        
        // --------- [Redis 초기화] ---------
        // 우리 프로젝트에서 쓰는 웨이팅(waiting:*), 캐시(waitingPolicy::*) 관련 키들을 수집해 삭제합니다.
        Set<String> waitingKeys = redisTemplate.keys("waiting:*");
        Set<String> cacheKeys = redisTemplate.keys("waitingPolicy:*");   
        Set<String> reservationKeys = redisTemplate.keys("reservation:*");
        
        if (waitingKeys != null && !waitingKeys.isEmpty()) {
            redisTemplate.delete(waitingKeys);
            log.info("✅ Redis 실시간 대기열 키 초기화 완료 ({}개)", waitingKeys.size());
	}
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
            log.info("✅ Redis Look-Aside 정책 캐시 초기화 완료 ({}개)", cacheKeys.size());
        }

        if (reservationKeys != null && !reservationKeys.isEmpty()) {
            redisTemplate.delete(reservationKeys);
            log.info("✅ Redis 예약 키 초기화 완료 ({}개)", reservationKeys.size());
        }
	}
	
	private String convertToSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
