package com.nowait.global.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/*
 * Redis Pub/Sub 채널 구독자.
 *
 * - 모든 Pod 가 동일 채널을 구독
 * - 메시지 수신 → 본 Pod 의 SseConnectionManager 에 위임
 * - 본 Pod 메모리에 해당 user 의 emitter 가 있으면 send, 없으면 무시 (정상)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseRedisSubscriber implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SseConnectionManager connectionManager;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String body = new String(message.getBody());
      SseMessage msg = objectMapper.readValue(body, SseMessage.class);
      connectionManager.sendLocal(msg.targetUserId(), msg.eventName(), msg.payloadJson());
    } catch (Exception e) {
      log.error("Failed to handle SSE pub/sub message", e);
    }
  }
}
