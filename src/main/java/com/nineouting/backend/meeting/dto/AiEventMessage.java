package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;

/** meeting-service → ai-service 회의 알림 (docs/services/ai-integration.md 2.4). wall: 백엔드가 받은 서버 시각 */
public record AiEventMessage(Long meetingId, String event, long atMs, LocalDateTime wall) {
}
