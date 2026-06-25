package com.nowait.global.config;

import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.entity.Slot;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.global.common.TimeZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * 롤링 배포 중에는 API/Worker 파드 여러 개가 거의 동시에 부팅하면서 이 러너를 동시에 실행한다.
 * 각 파드가 독립적으로 "없는 슬롯"을 계산해 같은 슬롯을 동시에 insert하면
 * UNIQUE 제약(restaurant_id, slot_date, slot_time) 위반으로 컨텍스트 기동이 실패하고 컨테이너가
 * 크래시 후 재시작된다. WaitingSessionDailyOpenScheduler와 동일한 Redis SETNX 락으로
 * 한 파드만 실행하게 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotSeedRunner implements ApplicationRunner {

    private static final String LOCK_KEY = "slot:seed:lock:startup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(3);

    private final String podId = UUID.randomUUID().toString();

    private final RestaurantRepository restaurantRepository;
    private final SlotRepository slotRepository;
    private final StringRedisTemplate redis;

    // 생성할 정시 슬롯 시간대
    private static final List<LocalTime> SLOT_TIMES = List.of(
        LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
        LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0),
        LocalTime.of(20, 0)
    );

    @Override
    public void run(ApplicationArguments args) {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, podId, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("SlotSeedRunner 건너뜀 — 다른 파드가 이미 실행 중이거나 방금 실행함.");
            return;
        }

        try {
            seedSlots();
        } finally {
            releaseLockIfOwner();
        }
    }

    @Transactional
    public void seedSlots() {
        LocalDate today = LocalDate.now(TimeZones.KST);
        LocalDate endDate = today.plusDays(30);

        // 이미 존재하는 슬롯을 "restaurantId_date_time" 문자열 Set으로 캐시
        Set<String> existing = slotRepository.findAll().stream()
            .map(s -> s.getRestaurant().getId() + "_" + s.getSlotDate() + "_" + s.getSlotTime())
            .collect(Collectors.toSet());

        List<Slot> toSave = new ArrayList<>();
        var restaurants = restaurantRepository.findAll();

        for (var restaurant : restaurants) {
            for (int dayOffset = 0; dayOffset < 30; dayOffset++) {
                LocalDate date = today.plusDays(dayOffset);
                for (LocalTime time : SLOT_TIMES) {
                    String key = restaurant.getId() + "_" + date + "_" + time;
                    if (!existing.contains(key)) {
                        toSave.add(Slot.builder()
                            .restaurant(restaurant)
                            .slotDate(date)
                            .slotTime(time)
                            .totalCount(4)
                            .minHeadcount(1)
                            .maxHeadcount(8)
                            .build());
                    }
                }
            }
        }

        slotRepository.saveAll(toSave);
        log.info("SlotSeedRunner 완료: {}개 슬롯 생성", toSave.size());
    }

    private void releaseLockIfOwner() {
        try {
            String owner = redis.opsForValue().get(LOCK_KEY);
            if (podId.equals(owner)) {
                redis.delete(LOCK_KEY);
            }
        } catch (Exception e) {
            log.warn("Failed to release slot-seed lock — will expire by TTL.", e);
        }
    }
}