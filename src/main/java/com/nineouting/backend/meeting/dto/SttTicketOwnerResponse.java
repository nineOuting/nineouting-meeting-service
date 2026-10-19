package com.nineouting.backend.meeting.dto;

/** stt-service가 입장권을 확인했을 때 돌려주는 연결 대상 */
public record SttTicketOwnerResponse(Long meetingId, Long userId) {
}
