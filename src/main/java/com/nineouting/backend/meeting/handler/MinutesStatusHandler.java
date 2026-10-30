package com.nineouting.backend.meeting.handler;

import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.meeting.domain.MinutesProgress;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-048: minutes-service의 검토 요청·확정을 받아 회의 목록·캘린더에 "검토 중·확정"을 표시한다.
 * 회의가 이미 지워졌으면(폴더 삭제) 할 일이 없다.
 */
@Component
@RequiredArgsConstructor
public class MinutesStatusHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final MeetingRepository meetingRepository;

    @Override
    public boolean supports(String eventType) {
        return MinutesReviewRequested.TYPE.equals(eventType) || MinutesConfirmed.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        if (MinutesReviewRequested.TYPE.equals(message.eventType())) {
            Long meetingId = message.payload(jsonMapper, MinutesReviewRequested.class).meetingId();
            meetingRepository.findById(meetingId).ifPresent(meeting -> meeting.changeMinutesStatus(MinutesProgress.REVIEWING));
            return;
        }
        Long meetingId = message.payload(jsonMapper, MinutesConfirmed.class).meetingId();
        meetingRepository.findById(meetingId).ifPresent(meeting -> meeting.changeMinutesStatus(MinutesProgress.CONFIRMED));
    }
}
