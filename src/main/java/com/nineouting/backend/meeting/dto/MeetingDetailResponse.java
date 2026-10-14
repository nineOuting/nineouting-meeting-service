package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.MeetingStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 회의 대기 화면 (REQ-068, 069), 이전 설정 불러오기 (REQ-060) */
public record MeetingDetailResponse(
        Long meetingId,
        Long workspaceId,
        Long folderId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String location,
        MeetingStatus status,
        PersonResponse owner,
        PersonResponse writer,
        List<PersonResponse> attendees,
        List<AgendaResponse> agendas,
        Long previousMeetingId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
