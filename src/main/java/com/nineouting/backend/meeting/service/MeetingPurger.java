package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.domain.MeetingMaterial;
import com.nineouting.backend.meeting.repository.AgendaEditRepository;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import com.nineouting.backend.meeting.repository.AiBatchRepository;
import com.nineouting.backend.meeting.repository.AiFeedRepository;
import com.nineouting.backend.meeting.repository.AiFeedbackRepository;
import com.nineouting.backend.meeting.repository.ConnectionTicketRepository;
import com.nineouting.backend.meeting.repository.LineAgendaRepository;
import com.nineouting.backend.meeting.repository.LiveSummaryRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingMaterialRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.ProgressEventRepository;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회의와 그 안의 참석자, 안건, 전사 텍스트, STT 기록, 입장권, AI 기록, 사전 자료(S3 파일 포함)를 지운다.
 * 폴더 영구 삭제(REQ-058)와 회의 영구 삭제(REQ-143)가 함께 쓴다. 남은 안건은 폴더 것이라 여기서 지우지 않는다.
 * 부르는 쪽이 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
public class MeetingPurger {

    private final AgendaStateRepository agendaStateRepository;
    private final LineAgendaRepository lineAgendaRepository;
    private final AgendaEditRepository agendaEditRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final AgendaRepository agendaRepository;
    private final TranscriptLineRepository transcriptLineRepository;
    private final ProgressEventRepository progressEventRepository;
    private final ConnectionTicketRepository connectionTicketRepository;
    private final AiFeedRepository aiFeedRepository;
    private final AiBatchRepository aiBatchRepository;
    private final LiveSummaryRepository liveSummaryRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final MeetingMaterialRepository materialRepository;
    private final MaterialStorage materialStorage;

    public void purge(Collection<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return;
        }
        agendaRepository.deleteByMeetingIdIn(meetingIds);
        transcriptLineRepository.deleteByMeetingIdIn(meetingIds);
        progressEventRepository.deleteByMeetingIdIn(meetingIds);
        connectionTicketRepository.deleteByMeetingIdIn(meetingIds);
        aiFeedRepository.deleteByMeetingIdIn(meetingIds);
        aiBatchRepository.deleteByMeetingIdIn(meetingIds);
        agendaStateRepository.deleteByMeetingIdIn(meetingIds);
        lineAgendaRepository.deleteByMeetingIdIn(meetingIds);
        agendaEditRepository.deleteByMeetingIdIn(meetingIds);
        liveSummaryRepository.deleteByMeetingIdIn(meetingIds);
        aiFeedbackRepository.deleteByMeetingIdIn(meetingIds);
        // 사전 자료 파일(S3)도 지운다 (REQ-067). 실패해도 기록만 남기고 나머지는 지운다
        materialRepository.findByMeetingIdIn(meetingIds).stream()
                .map(MeetingMaterial::getStorageKey)
                .forEach(materialStorage::deleteQuietly);
        materialRepository.deleteByMeetingIdIn(meetingIds);
        attendeeRepository.deleteByMeetingIdIn(meetingIds);
        meetingRepository.deleteAllByIdIn(meetingIds);
    }
}
