package com.nineouting.backend.meeting.dto;

/** WebSocket 연결 입장권 (음성 연결·회의방). expiresIn: 남은 초 */
public record TicketResponse(String ticket, long expiresIn) {
}
