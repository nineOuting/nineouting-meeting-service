package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.dto.RoomMessage;

/**
 * 회의방으로 보낼 일. 트랜잭션이 커밋된 뒤에만 보낸다 (MeetingRoomRelay).
 *
 * @param close true면 메시지를 보낸 뒤 회의방 연결을 모두 닫는다 (회의 마무리)
 */
public record RoomEvent(Long meetingId, RoomMessage message, boolean close) {

    public static RoomEvent send(Long meetingId, RoomMessage message) {
        return new RoomEvent(meetingId, message, false);
    }

    public static RoomEvent closeAfter(Long meetingId, RoomMessage message) {
        return new RoomEvent(meetingId, message, true);
    }
}
