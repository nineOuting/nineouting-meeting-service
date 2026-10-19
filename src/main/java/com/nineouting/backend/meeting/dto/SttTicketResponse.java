package com.nineouting.backend.meeting.dto;

/** 음성 연결 입장권 (docs/services/ai-integration.md 2.1). expiresIn: 남은 초 */
public record SttTicketResponse(String ticket, long expiresIn) {
}
