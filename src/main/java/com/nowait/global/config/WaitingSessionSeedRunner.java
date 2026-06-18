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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Order(2)   // SlotSeedRunner(기본 order=최하위) 이후 실행
@RequiredArgsConstructor
public class WaitingSessionSeedRunner implements ApplicationRunner {

    private static final int DEFAULT_MAX_WAITING = 30;

    private final RestaurantRepository restaurantRepository;
    private final WaitingSessionRepository waitingSessionRepository;
    private final WaitingRedisLuaExecutor waitingRedis;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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
}
