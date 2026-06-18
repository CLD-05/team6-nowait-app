package com.nowait.global.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.nowait.global.sse.SseEventPublisher;
import com.nowait.global.sse.SseRedisSubscriber;

@Configuration
public class RedisConfig {
	
	/*
	   * 🏪 스프링 통합 캐시 통제실 (CacheManager)
	   * - @Cacheable(cacheManager = "cacheManager") 코드가 이 빈(Bean)을 호출합니다.
	   * - 자바 객체를 Redis에 저장할 때 JSON 형태로 이쁘게 변환하고, 캐시 만료 시간(TTL)을 관리합니다.
	   */
	  @Bean
	  public CacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
	      // GenericJackson2JsonRedisSerializer()의 기본(no-arg) 생성자는 자체 내부 ObjectMapper를
	      // 새로 만드는데, Spring MVC 응답 직렬화에 쓰는 (JavaTimeModule, ParameterNamesModule 등이
	      // 이미 다 들어가 있는) ObjectMapper 빈과는 완전히 별개다. 그 깡통 ObjectMapper로는
	      // LocalDate/LocalTime 필드가 있는 DTO를 캐싱하는 순간 InvalidDefinitionException이 터지고
	      // (예: SlotResponse.date), 캐시 HIT 때 다시 읽어들이는 단계에서는 기본 생성자가 없는
	      // @Builder DTO(예: SlotResponse, SlotInfo)를 만들 방법이 없어 또 다른 역직렬화 에러로
	      // 이어진다. 직접 새 ObjectMapper를 만드는 대신, 이미 모든 모듈이 올바르게 붙어 있는
	      // 애플리케이션 ObjectMapper 빈을 그대로 복사해서 쓰고, 캐시 직렬화에 필요한
	      // (1) 타입 정보 보존(activateDefaultTyping), (2) private 필드 직접 접근만 추가한다.
	      ObjectMapper redisObjectMapper = objectMapper.copy();
	      redisObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
	      BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
	          .allowIfSubType(Object.class)
	          .build();
	      // DefaultTyping.NON_FINAL은 Collection/Map 같은 컨테이너 타입 자체에는 타입 정보를 안 붙인다
	      // (제네릭 엘리먼트 타입이 선언 타입과 같으면 더더욱 안 붙인다). 그래서 List<RestaurantHourRequest>
	      // 같은 최상위 컬렉션 캐시 값은 쓸 때 타입 태그 없이 평범한 JSON 배열로 저장되는데, 읽을 때는
	      // 대상 타입이 Object라서 무조건 타입 태그가 있을 거라 기대하다가 깨진다(MismatchedInputException).
	      // EVERYTHING은 컨테이너 포함 모든 비-스칼라 값에 타입 정보를 빠짐없이 붙여서 이 비대칭을 없앤다.
	      redisObjectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.WRAPPER_ARRAY);

	      // 1. 기본 캐시 설정 정의
	      RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
	          // ⏱️ 중요! 캐시의 기본 수명을 1시간(60분)으로 설정합니다.
	          // (너무 길면 메모리가 부족하고, 너무 짧으면 DB를 자주 찌릅니다)
	          .entryTtl(Duration.ofHours(1))

	          // 🔑 캐시 키(Key)는 순수 문자열로 저장합니다. (예: restaurant::1)
	          .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))

	          // 📦 캐시 값(Value)은 자바 객체를 그대로 저장할 수 있도록 범용적인 Jackson JSON 직렬화 방식을 씁니다.
	          // GenericJackson2JsonRedisSerializer를 쓰면 DTO 객체가 JSON 문자열로 Redis에 이쁘게 들어갑니다.
	          .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
	              new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer(redisObjectMapper)
	          ));

	      // 2. 설정이 완료된 Redis 캐시 매니저를 빌드하여 반환합니다.
	      return RedisCacheManager.RedisCacheManagerBuilder
	          .fromConnectionFactory(factory)
	          .cacheDefaults(config)
	          .build();
	  }

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