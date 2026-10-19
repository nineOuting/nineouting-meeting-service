package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;

/**
 * meeting-service → ai-service 회의 알림 (docs/services/ai-integration.md 2.4). wall: 백엔드가 받은 서버 시각.
 * meetingId는 ai-service가 문자열로 받는다.
 */
public record AiEventMessage(String meetingId, String event, long atMs, LocalDateTime wall) {
}
