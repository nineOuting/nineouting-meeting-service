package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.domain.TodoCheck;
import com.nineouting.backend.meeting.dto.RoomMessage;
import com.nineouting.backend.meeting.repository.TodoCheckRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의 중 할 일 완료 체크 (디자인 Version02 Live 할 일 표). 할 일 자체는 ai-service 안건 보드가 만든다.
 * 체크는 회의록 작성자가 진행 중에 하고, 바뀌면 회의방에 전체 목록을 보낸다
 */
@Service
@RequiredArgsConstructor
public class TodoCheckService {

    private final TodoCheckRepository todoCheckRepository;
    private final MeetingAccess access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 회의를 볼 수 있는 사람. 체크한 순서대로 */
    @Transactional(readOnly = true)
    public List<String> list(Long meetingId, Long userId) {
        access.requireViewable(meetingId, userId);
        return itemIds(meetingId);
    }

    @Transactional
    public List<String> check(Long meetingId, Long userId, String itemId, boolean done) {
        access.requireWriterInProgress(meetingId, userId);
        if (done) {
            todoCheckRepository.insertIgnoringDuplicate(meetingId, itemId, userId, LocalDateTime.now(clock));
        } else {
            todoCheckRepository.deleteOne(meetingId, itemId);
        }
        List<String> itemIds = itemIds(meetingId);
        events.publishEvent(RoomEvent.send(meetingId, RoomMessage.todoChecks(itemIds)));
        return itemIds;
    }

    private List<String> itemIds(Long meetingId) {
        return todoCheckRepository.findByMeetingIdOrderByCheckedAt(meetingId).stream().map(TodoCheck::getItemId).toList();
    }
}
