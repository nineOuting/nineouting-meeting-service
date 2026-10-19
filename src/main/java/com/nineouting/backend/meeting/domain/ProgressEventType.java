package com.nineouting.backend.meeting.domain;

import java.util.Locale;

/** 회의 진행 알림 종류. stt-service·ai-service와는 소문자(start, pause …)로 주고받는다 */
public enum ProgressEventType {
    START,
    /** 멈춤·재개는 REQ-119 */
    PAUSE,
    RESUME,
    END;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ProgressEventType fromWireName(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
