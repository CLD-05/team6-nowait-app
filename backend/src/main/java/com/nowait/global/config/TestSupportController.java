package com.nowait.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/global/test")
@RequiredArgsConstructor
public class TestSupportController {
	
	private final TestDatabaseCleanUpService databaseCleanUpService;

	// application.yml/env(TEST_SECRET_TOKEN)에 명시적으로 설정해야만 동작합니다. 기본값을 두지 않음으로써
	// 토큰을 설정하지 않은 환경(예: 운영)에서는 이 엔드포인트가 절대 통과되지 않도록 합니다.
	@Value("${test.secret-token:}")
	private String secretToken;

	@PostMapping("/reset")
	public ResponseEntity<String> resetDatabase(
			@RequestHeader(value = "X-Test-Token", required = false) String requestToken
			) {
		// 🔒 시크릿 토큰 보안 검증 — secretToken이 설정되지 않았으면 항상 거부
		if (secretToken == null || secretToken.isBlank()
				|| requestToken == null || !requestToken.equals(secretToken)) {
			log.warn("올바르지 않은 토큰으로 DB 초기화 요청이 거부되었습니다.");
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body("인증 토큰이 올바르지 않습니다. 데이터베이스 초기화가 거부되었습니다.");
		}
		
		try {
			databaseCleanUpService.execute();
			return ResponseEntity.ok("데이터베이스와 Redis 대기열이 성공적으로 완전히 초기화되었습니다.");
		} catch (Exception e) {
			log.error("초기화 중 시스템 에러 발생: ", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("초기화 중 에러가 발생하였습니다: " + e.getMessage());
		}
	}
}
