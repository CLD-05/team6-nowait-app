package com.nowait.global.config;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.type.DayOfWeek;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 예약 생성(ReservationService.createReservation)은 식당의 요일별 restaurant_hours 행이
 * 반드시 있어야 영업시간 검증을 통과한다. RestaurantService.registerRestaurant()를 거쳐
 * 등록된 식당은 누락된 요일을 11:00~22:00 기본값으로 자동 채워주지만(같은 클래스 75-84행
 * 참고), V1 마이그레이션으로 직접 INSERT된 시드 식당들은 그 경로를 타지 않아
 * restaurant_hours가 전혀 없다. 그 결과 슬롯은 보여도 예약 생성이 전부
 * OPERATING_INFO_NOT_FOUND로 실패한다. 같은 기본값(11:00~22:00, 휴무 없음)을
 * idempotent하게 채워 넣어 예약 기능이 즉시 동작하도록 한다.
 *
 * 주의: Restaurant 엔티티는 V1 시드 데이터의 open_time/close_time/closed_days 컬럼을
 * 매핑하지 않으므로(필드 자체가 없음) 식당별 실제 영업시간을 읽어올 수 없다. 점주는
 * StoreManagementPage에서 언제든 정확한 시간으로 덮어쓸 수 있다.
 */
@Slf4j
@Component
@Order(0) // SlotSeedRunner/WaitingSessionSeedRunner 보다 먼저 실행
@RequiredArgsConstructor
public class RestaurantHourSeedRunner implements ApplicationRunner {

    private static final LocalTime DEFAULT_OPEN = LocalTime.of(11, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(22, 0);

    private final RestaurantRepository restaurantRepository;
    private final RestaurantHourRepository restaurantHourRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<RestaurantHour> toSave = new ArrayList<>();

        for (Restaurant restaurant : restaurantRepository.findAll()) {
            if (!restaurantHourRepository.findByRestaurantId(restaurant.getId()).isEmpty()) {
                continue; // 이미 점주가 설정했거나 이전에 시드된 경우 건너뜀
            }

            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                toSave.add(RestaurantHour.builder()
                    .restaurant(restaurant)
                    .dayOfWeek(dayOfWeek)
                    .openTime(DEFAULT_OPEN)
                    .closeTime(DEFAULT_CLOSE)
                    .isRegularHoliday("N")
                    .build());
            }
        }

        restaurantHourRepository.saveAll(toSave);
        log.info("RestaurantHourSeedRunner 완료: {}개 영업시간 행 생성", toSave.size());
    }
}
