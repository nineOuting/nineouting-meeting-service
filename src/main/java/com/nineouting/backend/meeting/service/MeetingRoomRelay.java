package com.nineouting.backend.meeting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 저장이 커밋된 뒤에만 회의방으로 보낸다 (롤백된 변경이 화면에 보이지 않게) */
@Component
@RequiredArgsConstructor
public class MeetingRoomRelay {

    private final MeetingRoomHub hub;

    @TransactionalEventListener
    public void relay(RoomEvent event) {
        hub.send(event.meetingId(), event.message());
        if (event.close()) {
            hub.close(event.meetingId());
        }
    }
}
