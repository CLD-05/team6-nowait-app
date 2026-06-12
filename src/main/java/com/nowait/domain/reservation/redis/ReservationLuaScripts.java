package com.nowait.domain.reservation.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/*
 * 예약 Lua 스크립트 Spring Bean 등록.
 * - 앱 시작 시 1회 로드 → 이후 EVAL 시 sha1 캐시 활용
 * - 반환 타입은 List<Object> (혼합 타입)
 */
@Configuration
public class ReservationLuaScripts {

  @Bean("reservationCreateScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> reservationCreateScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/reservation/create.lua"));
    script.setResultType(List.class);
    return script;
  }

  @Bean("reservationCancelScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> reservationCancelScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/reservation/cancel.lua"));
    script.setResultType(List.class);
    return script;
  }

  @Bean("reservationVisitScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> reservationVisitScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/reservation/visit.lua"));
    script.setResultType(List.class);
    return script;
  }

  @Bean("reservationNoShowScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> reservationNoShowScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/reservation/no-show.lua"));
    script.setResultType(List.class);
    return script;
  }
}
