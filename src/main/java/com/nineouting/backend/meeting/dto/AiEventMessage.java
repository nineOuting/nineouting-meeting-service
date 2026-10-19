package com.nineouting.backend.meeting.dto;

import java.time.OffsetDateTime;

/**
 * meeting-service → ai-service 회의 알림 (docs/services/ai-integration.md 2.4).
 * ai-service 입력 형식에 맞춰 meetingId는 문자열, wall(백엔드가 받은 서버 시각)은 +09:00을 붙인 ISO 8601로 보낸다.
 */
public record AiEventMessage(String meetingId, String event, long atMs, OffsetDateTime wall) {
}
