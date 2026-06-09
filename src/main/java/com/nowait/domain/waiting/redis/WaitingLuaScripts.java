package com.nowait.domain.waiting.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/*
 * 웨이팅 Lua 스크립트를 Spring Bean 으로 등록.
 *
 * - 앱 시작 시 1회 로드 → 이후 EVAL 호출 시 sha1 캐시 활용
 * - Lua 반환 타입은 List<Object> (혼합 타입 포함)
 */
@Configuration
public class WaitingLuaScripts {

  @Bean("waitingRegisterScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> waitingRegisterScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/waiting/register.lua"));
    script.setResultType(List.class);
    return script;
  }

  @Bean("waitingCancelScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> waitingCancelScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/waiting/cancel.lua"));
    script.setResultType(List.class);
    return script;
  }

  @Bean("waitingEnterScript")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public RedisScript<List> waitingEnterScript() {
    DefaultRedisScript script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/waiting/enter.lua"));
    script.setResultType(List.class);
    return script;
  }
}
