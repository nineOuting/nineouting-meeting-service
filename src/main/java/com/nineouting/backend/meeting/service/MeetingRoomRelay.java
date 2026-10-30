package com.nineouting.backend.meeting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 저장이 커밋된 뒤에만 회의방으로 보낸다 (롤백된 변경이 화면에 보이지 않게).
 * 참석자가 다른 인스턴스에 붙어 있을 수 있어 Redis로 모든 인스턴스에 나눠 보낸다 (RoomFanout).
 */
@Component
@RequiredArgsConstructor
public class MeetingRoomRelay {

    private final RoomFanout fanout;

    @TransactionalEventListener
    public void relay(RoomEvent event) {
        fanout.publish(event);
    }
}
