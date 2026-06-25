package com.nowait.global.config;

import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.repository.WaitingSessionRepository;
import com.nowait.global.common.TimeZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
 * SlotSeedRunner와 동일한 이유로 Redis SETNX 락이 필요하다: 롤링 배포 중 여러 파드가 동시에
 * 부팅하면 각자 "오늘 세션 없음"으로 판단해 같은 (restaurant_id, session_date)를 동시에
 * insert하려다 UNIQUE 제약 위반으로 컨텍스트 기동이 실패할 수 있다.
 */
@Slf4j
@Component
@Order(2)   // SlotSeedRunner(기본 order=최하위) 이후 실행
@RequiredArgsConstructor
public class WaitingSessionSeedRunner implements ApplicationRunner {

    private static final int DEFAULT_MAX_WAITING = 30;
    private static final String LOCK_KEY = "waiting-session:seed:lock:startup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(3);

    private final String podId = UUID.randomUUID().toString();

    private final RestaurantRepository restaurantRepository;
    private final WaitingSessionRepository waitingSessionRepository;
    private final WaitingRedisLuaExecutor waitingRedis;
    private final StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, podId, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("WaitingSessionSeedRunner 건너뜀 — 다른 파드가 이미 실행 중이거나 방금 실행함.");
            return;
        }

        try {
            seedSessions();
        } finally {
            releaseLockIfOwner();
        }
    }

    @Transactional
    public void seedSessions() {
        LocalDate today = LocalDate.now(TimeZones.KST);
        LocalDateTime now = LocalDateTime.now(TimeZones.KST);

        var restaurants = restaurantRepository.findAll();
        Set<Long> existingRestaurantIds = new HashSet<>(
                waitingSessionRepository.findRestaurantIdsBySessionDate(today));

        List<WaitingSession> toInit = new ArrayList<>();

        for (var restaurant : restaurants) {
            Long restaurantId = restaurant.getId();
            if (!existingRestaurantIds.contains(restaurantId)) {
                WaitingSession session = WaitingSession.open(
                        restaurantId, today, DEFAULT_MAX_WAITING, now);
                waitingSessionRepository.save(session);
                toInit.add(session);
            }
        }

        // DB flush 이후 Redis 초기화 (session.getId() 확정 후)
        toInit.forEach(session -> waitingRedis.initSession(session.getId()));

        log.info("WaitingSessionSeedRunner 완료: {}개 세션 생성", toInit.size());
    }

    private void releaseLockIfOwner() {
        try {
            String owner = redis.opsForValue().get(LOCK_KEY);
            if (podId.equals(owner)) {
                redis.delete(LOCK_KEY);
            }
        } catch (Exception e) {
            log.warn("Failed to release waiting-session-seed lock — will expire by TTL.", e);
        }
    }
}
