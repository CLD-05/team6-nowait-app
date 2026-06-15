package com.nowait.global.security.jwt;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {
	
	private final StringRedisTemplate redisTemplate;
	private final JwtTokenProvider jwtTokenProvider;
	
	private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
	
	/**
     * 🔒 토큰을 Redis 블랙리스트에 등록 (로그아웃 처리)
     */
	public void registerBlacklist(String token) {
		// 1. 토큰이 유효한지 먼저 확인
		if (!jwtTokenProvider.validate(token)) {
			throw new IllegalArgumentException("이미 만료되었거나 유효하지 않은 토큰입니다.");
		}
		
		// 2. 토큰의 남은 만료 시간을 계산 (예: 30분 남음 -> 1800초)
		long remainingSeconds = jwtTokenProvider.getRemainingValiditySeconds(token);
		
		if (remainingSeconds > 0) {
			String blacklistKey = BLACKLIST_KEY_PREFIX + token;
			
			// 3. Redis에 [jwt:blacklist:토큰값 = logout] 형태로 저장하고 남은 시간만큼 TTL 설정
			redisTemplate.opsForValue().set(
					blacklistKey, 
					"logout",
					remainingSeconds,
					TimeUnit.SECONDS);
			
			log.info("토큰이 블랙리스트에 등록되었습니다. 남은 수명: {}초", remainingSeconds);
		}
	}
		
	/**
     * 🔍 해당 토큰이 블랙리스트에 등록된 토큰인지 확인
     */
    public boolean isBlacklisted(String token) {
        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    }
}


