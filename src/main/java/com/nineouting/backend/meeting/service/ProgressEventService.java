package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.domain.ProgressEventType;
import com.nineouting.backend.meeting.repository.ProgressEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의 진행 알림 기록 (docs/services/ai-integration.md 2.3). 회의 상태는 바꾸지 않는다.
 * 실제 시각은 STT가 보내지 않으므로 받은 시점의 서버 시각을 붙인다. ai-service로는 AiFeedJob이 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class ProgressEventService {

    private final ProgressEventRepository eventRepository;
    private final MeetingAccess access;
    private final Clock clock;

    @Transactional
    public void record(Long meetingId, String event, long atMs) {
        access.requireStarted(meetingId);
        eventRepository.insertIgnoringDuplicate(meetingId, ProgressEventType.fromWireName(event).name(), atMs,
                LocalDateTime.now(clock));
    }
}
