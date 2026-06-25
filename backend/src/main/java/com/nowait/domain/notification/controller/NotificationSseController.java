package com.nowait.domain.notification.controller;

import com.nowait.global.exception.BusinessException;
import com.nowait.global.security.principal.CustomUserDetails;
import com.nowait.global.sse.SseConnectionManager;
import com.nowait.global.sse.SseTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/*
 * SSE 전용 컨트롤러.
 *
 * - POST /api/v1/notifications/stream/ticket : JWT 헤더 인증 → 티켓 발급(TTL 동안 유효)
 * - GET  /api/v1/notifications/stream        : 티켓으로 SSE 연결 (JWT 우회, 컨트롤러 내부에서 검증)
 *
 * SseEmitter 반환 컨트롤러에서 예외를 그대로 던지면 GlobalExceptionHandler 가
 * text/event-stream 응답에 ErrorResponse(JSON) 직렬화를 시도하다 HttpMessageNotWritableException
 * 으로 터지고, 프론트엔드에는 그냥 연결만 끊긴 것으로 보여 "웨이팅 정보를 찾을 수 없음"으로 표시된다.
 * 그래서 SSE 진입 단계의 모든 예외는 컨트롤러 안에서 잡아 error 이벤트로 스트림에 흘려준다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class NotificationSseController {

  private final SseTicketService ticketService;
  private final SseConnectionManager connectionManager;

  /* SSE 연결용 티켓 발급 (JWT 인증 필요) */
  @PostMapping("/api/v1/notifications/stream/ticket")
  public Map<String, String> issueTicket(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String ticket = ticketService.issue(userDetails.getUserId());
    return Map.of("ticket", ticket);
  }

  /* SSE 스트림 연결 (티켓 검증) */
  @GetMapping(value = "/api/v1/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam("ticket") String ticket) {
    try {
      Long userId = ticketService.consume(ticket);
      return connectionManager.connect(userId);
    } catch (BusinessException e) {
      log.warn("SSE init failed (business): code={}, msg={}", e.getErrorCode().name(), e.getMessage());
      return errorEmitter(e.getErrorCode().name(), e.getMessage());
    } catch (Exception e) {
      log.error("SSE init failed (unexpected)", e);
      return errorEmitter("INTERNAL_ERROR", "서버 내부 오류");
    }
  }

  private SseEmitter errorEmitter(String code, String message) {
    // 즉시 complete 할 거지만 timeout=0 이면 일부 컨테이너에서 즉시 AsyncRequestTimeoutException
    // 으로 빠질 수 있어 짧게 명시. send/complete 가 그 전에 끝난다.
    SseEmitter emitter = new SseEmitter(5_000L);
    try {
      emitter.send(SseEmitter.event()
          .name("error")
          .data(Map.of("code", code, "message", message)));
    } catch (IOException ignored) {
    }
    emitter.complete();
    return emitter;
  }
}
