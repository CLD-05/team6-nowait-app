package com.nowait.domain.notification.controller;

import com.nowait.global.security.principal.CustomUserDetails;
import com.nowait.global.sse.SseConnectionManager;
import com.nowait.global.sse.SseTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/*
 * SSE 전용 컨트롤러.
 *
 * - POST /api/notifications/stream/ticket : JWT 헤더 인증 → 단발 티켓 발급(10초, 1회용)
 * - GET  /api/notifications/stream        : 티켓으로 SSE 연결 (JWT 우회, 컨트롤러 내부에서 검증)
 */
@RestController
@RequiredArgsConstructor
public class NotificationSseController {

  private final SseTicketService ticketService;
  private final SseConnectionManager connectionManager;

  /* SSE 연결용 단발 티켓 발급 (JWT 인증 필요) */
  @PostMapping("/api/notifications/stream/ticket")
  public Map<String, String> issueTicket(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String ticket = ticketService.issue(userDetails.getUserId());
    return Map.of("ticket", ticket);
  }

  /* SSE 스트림 연결 (티켓 검증 후 1회 사용) */
  @GetMapping(value = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam("ticket") String ticket) {
    Long userId = ticketService.consume(ticket);
    return connectionManager.connect(userId);
  }
}
