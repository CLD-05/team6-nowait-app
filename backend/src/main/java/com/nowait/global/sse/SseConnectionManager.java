package com.nowait.global.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*
 * 단일 Pod 내부의 SSE 커넥션 보관소.
 *
 * - userId 당 1개 emitter (재연결 시 이전 거 교체)
 * - emitter 가 죽으면(완료/타임아웃/에러) 자동 제거
 * - 멀티 Pod 환경에서는 본 manager 의 sendLocal() 을 SseRedisSubscriber 가 호출하는 방식으로 fan-out
 */
@Slf4j
@Component
public class SseConnectionManager {

  /* userId -> SseEmitter */
  private final ConcurrentMap<Long, SseEmitter> connections = new ConcurrentHashMap<>();

  private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30분

  /* 새 SSE 연결 등록. 동일 사용자 기존 커넥션은 닫고 교체. */
  public SseEmitter connect(Long userId) {
    SseEmitter prev = connections.remove(userId);
    if (prev != null) {
      try {
        prev.complete();
      } catch (Exception ignored) {
      }
    }

    SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
    connections.put(userId, emitter);

    emitter.onCompletion(() -> remove(userId, emitter));
    emitter.onTimeout(() -> remove(userId, emitter));
    emitter.onError(e -> remove(userId, emitter));

    /* 초기 연결 확인 이벤트 — 프록시/로드밸런서 buffering 우회 */
    try {
      emitter.send(SseEmitter.event().name("connect").data("ok"));
    } catch (IOException e) {
      remove(userId, emitter);
    }
    return emitter;
  }

  /* 본 Pod 메모리에 있는 emitter 로만 전송. 없으면 무시. */
  public void sendLocal(Long userId, String eventName, String payloadJson) {
    SseEmitter emitter = connections.get(userId);
    if (emitter == null) return;

    try {
      emitter.send(SseEmitter.event().name(eventName).data(payloadJson));
    } catch (IOException e) {
      log.warn("SSE send failed. userId={}, removing emitter.", userId);
      remove(userId, emitter);
    } catch (Exception e) {
      log.error("Unexpected SSE error. userId={}", userId, e);
      remove(userId, emitter);
    }
  }

  /* 현재 보관 중인 활성 커넥션 수 (모니터링용) */
  public int activeConnectionCount() {
    return connections.size();
  }

  /*
   * ALB / 중간 프록시 idle timeout(보통 60s) 보다 짧은 주기로 ping 을 보내
   * 커넥션이 끊기지 않도록 한다. comment 이벤트(`:` 로 시작)는 EventSource 가
   * 무시하므로 프론트 핸들러에 영향 없음.
   */
  @Scheduled(fixedRate = 15_000L)
  public void heartbeat() {
    for (Map.Entry<Long, SseEmitter> entry : connections.entrySet()) {
      Long userId = entry.getKey();
      SseEmitter emitter = entry.getValue();
      try {
        emitter.send(SseEmitter.event().comment("ping"));
      } catch (IOException e) {
        log.debug("SSE heartbeat failed. userId={}, removing emitter.", userId);
        remove(userId, emitter);
      } catch (Exception e) {
        log.warn("Unexpected SSE heartbeat error. userId={}", userId, e);
        remove(userId, emitter);
      }
    }
  }

  private void remove(Long userId, SseEmitter target) {
    /* 다른 emitter 로 교체된 경우 잘못 제거하지 않도록 동일 ref 확인 */
    connections.computeIfPresent(userId, (k, v) -> v == target ? null : v);
  }
}
