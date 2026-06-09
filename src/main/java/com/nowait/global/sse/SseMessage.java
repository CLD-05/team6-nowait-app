package com.nowait.global.sse;

/*
 * Pod 간 SSE 메시지 전송용 페이로드.
 * Redis Pub/Sub 채널을 통해 직렬화되어 흐름.
 *
 * - targetUserId : 알림 받을 사용자 식별
 * - eventName    : SseEmitter 이벤트 이름 (프론트 addEventListener 와 매칭)
 * - payloadJson  : 실제 데이터(이미 JSON 직렬화된 문자열)
 */
public record SseMessage(
    Long targetUserId,
    String eventName,
    String payloadJson
) {}
