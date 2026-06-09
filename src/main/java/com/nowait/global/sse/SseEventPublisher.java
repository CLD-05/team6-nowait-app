package com.nowait.global.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
 * SSE 이벤트를 Redis Pub/Sub 채널로 publish.
 *
 * - 본 Pod 안에서만 fan-out 하지 않고, 모든 Pod 가 같은 채널을 구독하므로
 *   사용자의 SSE 커넥션이 어느 Pod 에 붙어있든 도달.
 * - 알림 발송 측(예: NotificationService)은 본 클래스만 호출하면 끝.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventPublisher {

  public static final String CHANNEL = "sse:events";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public void publish(Long targetUserId, String eventName, Object payload) {
    try {
      String payloadJson = objectMapper.writeValueAsString(payload);
      SseMessage msg = new SseMessage(targetUserId, eventName, payloadJson);
      String envelope = objectMapper.writeValueAsString(msg);
      redisTemplate.convertAndSend(CHANNEL, envelope);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize SSE payload. userId={}", targetUserId, e);
    } catch (Exception e) {
      log.error("Failed to publish SSE message. userId={}", targetUserId, e);
    }
  }
}
