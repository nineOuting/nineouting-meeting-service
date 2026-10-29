package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * 지금 안건 보드 (REQ-085~097, 124~126). board는 ai-service가 보낸 JSON 그대로다 (ai-service docs/api/agenda-*.md).
 * 아직 보드가 오지 않았으면 board가 null이다.
 *
 * @param currentAgendaId 줄별 분류가 본 지금 논의 중인 안건
 */
public record AgendaBoardResponse(Long meetingId, long version, Long currentAgendaId, JsonNode board,
                                  LocalDateTime receivedAt) {
}
