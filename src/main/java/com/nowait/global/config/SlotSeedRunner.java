package com.nowait.global.config;

import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.slot.repository.SlotRepository;
import com.nowait.domain.slot.entity.Slot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotSeedRunner implements ApplicationRunner {

    private final RestaurantRepository restaurantRepository;
    private final SlotRepository slotRepository;

    // 생성할 정시 슬롯 시간대
    private static final List<LocalTime> SLOT_TIMES = List.of(
        LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
        LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0),
        LocalTime.of(20, 0)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now();
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
}