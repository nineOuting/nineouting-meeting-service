package com.nineouting.backend.meeting.handler;

import com.nineouting.backend.common.event.workspace.FolderDeleted;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.AiBatchRepository;
import com.nineouting.backend.meeting.repository.AiFeedRepository;
import com.nineouting.backend.meeting.repository.ConnectionTicketRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.PendingAgendaRepository;
import com.nineouting.backend.meeting.repository.ProgressEventRepository;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-115: 폴더(하위 폴더 포함)가 지워지면 그 안의 회의·참석자·안건·남은 안건과 전사 텍스트·STT 기록·입장권을 지운다.
 * 알림은 보내지 않는다.
 * inbox가 이벤트마다 트랜잭션을 열어 부른다.
 */
@Component
@RequiredArgsConstructor
public class FolderDeletedHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final AgendaRepository agendaRepository;
    private final PendingAgendaRepository pendingAgendaRepository;
    private final TranscriptLineRepository transcriptLineRepository;
    private final ProgressEventRepository progressEventRepository;
    private final ConnectionTicketRepository connectionTicketRepository;
    private final AiFeedRepository aiFeedRepository;
    private final AiBatchRepository aiBatchRepository;

    @Override
    public boolean supports(String eventType) {
        return FolderDeleted.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        FolderDeleted event = message.payload(jsonMapper, FolderDeleted.class);
        if (event.folderIds() == null || event.folderIds().isEmpty()) {
            return;
        }
        List<Long> meetingIds = meetingRepository.findIdsByFolderIdIn(event.folderIds());
        if (!meetingIds.isEmpty()) {
            agendaRepository.deleteByMeetingIdIn(meetingIds);
            transcriptLineRepository.deleteByMeetingIdIn(meetingIds);
            progressEventRepository.deleteByMeetingIdIn(meetingIds);
            connectionTicketRepository.deleteByMeetingIdIn(meetingIds);
            aiFeedRepository.deleteByMeetingIdIn(meetingIds);
            aiBatchRepository.deleteByMeetingIdIn(meetingIds);
            attendeeRepository.deleteByMeetingIdIn(meetingIds);
            meetingRepository.deleteAllByIdIn(meetingIds);
        }
        pendingAgendaRepository.deleteByFolderIdIn(event.folderIds());
    }
}
