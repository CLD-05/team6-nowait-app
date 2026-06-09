package com.nowait.domain.notification.type;

public enum NotificationType {

    /* 점주 호출 — 즉시 도달 필요 → SSE push */
    WAITING_CALLED(true),

    /* 앞 5팀 — 폴링으로 충분 */
    WAITING_NEAR_CALL(false),

    RESERVATION_CONFIRMED(false),
    RESERVATION_CANCELLED(false);

    private final boolean realtime;

    NotificationType(boolean realtime) {
        this.realtime = realtime;
    }

    /* SSE 즉시 전송이 필요한 타입인지 */
    public boolean isRealtime() {
        return realtime;
    }
}
