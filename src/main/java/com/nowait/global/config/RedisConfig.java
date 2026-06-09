package com.nowait.global.config;

import com.nowait.global.sse.SseEventPublisher;
import com.nowait.global.sse.SseRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
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

  /* SSE Pub/Sub 채널 토픽 */
  @Bean
  public ChannelTopic sseEventTopic() {
    return new ChannelTopic(SseEventPublisher.CHANNEL);
  }

  /*
   * SSE 이벤트 구독 컨테이너.
   * 모든 Pod 가 같은 채널을 구독하여 사용자 SSE 커넥션이 어느 Pod 에 있든 전달 가능.
   */
  @Bean
  public RedisMessageListenerContainer sseRedisListenerContainer(
      RedisConnectionFactory factory,
      SseRedisSubscriber subscriber,
      ChannelTopic sseEventTopic) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(subscriber, sseEventTopic);
    return container;
  }
}