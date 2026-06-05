package com.nowait.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  /*
   * 문자열 키/값 전용 템플릿. INCR/DECR/SET/GET 등 단순 카운터/문자열 작업에 사용.
   * Spring Boot 가 RedisConnectionFactory 자동 구성해줌.
   */
  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }

  /*
   * 범용 RedisTemplate (Object 직렬화 필요한 경우 대비).
   * 지금은 안 쓰지만 향후 Hash/List 같은 자료구조 사용 시 유용.
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    return template;
  }
}