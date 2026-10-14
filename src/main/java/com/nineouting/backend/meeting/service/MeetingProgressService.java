package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingAttendee;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import com.nineouting.backend.meeting.dto.AddAgendaRequest;
import com.nineouting.backend.meeting.dto.AgendaResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.PendingAgendaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의 진행 중 상태 바꾸기 (1차): 시작, 안건 추가, 다음 회의로 넘기기, 마무리.
 * 전사 텍스트·안건 보드·AI 연동은 2차 (docs/services/meeting-service.md 0장).
 */
@Service
@RequiredArgsConstructor
public class MeetingProgressService {

    private final AgendaRepository agendaRepository;
    private final PendingAgendaRepository pendingAgendaRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final MeetingAccess access;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    /** REQ-070. 참석자 누구나, 예정 상태면 언제든 */
    @Transactional
    public void start(Long meetingId, Long userId) {
        Meeting meeting = access.requireAttendee(meetingId, userId);
        if (!meeting.is(MeetingStatus.SCHEDULED)) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_STARTED);
        }
        meeting.start(LocalDateTime.now(clock));
    }

    /** REQ-113. 회의록 작성자, 진행 중. 맨 뒤에 붙인다. */
    @Transactional
    public AgendaResponse addAgenda(Long meetingId, Long userId, AddAgendaRequest request) {
        access.requireWriterInProgress(meetingId, userId);
        List<Agenda> agendas = agendaRepository.findByMeetingIdOrderByPosition(meetingId);
        int position = agendas.isEmpty() ? 0 : agendas.getLast().getPosition() + 1;
        Agenda agenda = agendaRepository.save(new Agenda(meetingId, position, request.title(), request.decisionPoint(),
                null, true));
        return AgendaResponse.from(agenda);
    }

    /**
     * REQ-083. 고른 안건을 폴더의 남은 안건으로 저장한다.
     * 남은 안건에서 불러온 안건이면 같은 줄을 다시 대기로 돌리고, 이미 넘긴 안건은 다시 넘기지 않는다.
     */
    @Transactional
    public void carryOver(Long meetingId, Long userId, List<Long> agendaIds) {
        Meeting meeting = access.requireWriterInProgress(meetingId, userId);
        Map<Long, Agenda> agendas = agendaRepository.findByMeetingIdOrderByPosition(meetingId).stream()
                .collect(Collectors.toMap(Agenda::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long agendaId : agendaIds.stream().distinct().toList()) {
            Agenda agenda = agendas.get(agendaId);
            if (agenda == null) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            }
            if (agenda.getPendingAgendaId() == null) {
                PendingAgenda pending = pendingAgendaRepository.save(new PendingAgenda(meeting.getWorkspaceId(),
                        meeting.getFolderId(), agenda.getTitle(), agenda.getDecisionPoint(), meetingId, now));
                agenda.linkPendingAgenda(pending.getId());
                continue;
            }
            pendingAgendaRepository.findByIdForUpdate(agenda.getPendingAgendaId())
                    .filter(pending -> pending.isTakenBy(meetingId))
                    .ifPresent(pending -> pending.carryAgain(meetingId, now));
        }
    }

    /**
     * REQ-087. 회의록 작성자, 진행 중 → 끝남. 불러와서 다시 넘기지 않은 남은 안건은 처리됨.
     * minutes-service가 회의록(작성 중)을 만들도록 MeetingFinished를 남긴다.
     */
    @Transactional
    public void finish(Long meetingId, Long userId) {
        Meeting meeting = access.requireWriterInProgress(meetingId, userId);
        meeting.finish(LocalDateTime.now(clock));
        pendingAgendaRepository.findByTakenMeetingIdAndStatus(meetingId, PendingAgendaStatus.IN_PROGRESS)
                .forEach(PendingAgenda::done);

        List<Long> attendees = attendeeRepository.findByMeetingIdOrderById(meetingId).stream()
                .map(MeetingAttendee::getUserId)
                .toList();
        outboxEventWriter.append(MeetingFinished.TOPIC, meetingId.toString(), MeetingFinished.TYPE,
                new MeetingFinished(meetingId, meeting.getWorkspaceId(), meeting.getFolderId(), meeting.getTitle(),
                        meeting.getStartAt(), meeting.getWriterUserId(), attendees));
    }
}
