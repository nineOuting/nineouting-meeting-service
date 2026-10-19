package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.MinutesProgress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회의 대기 화면 (REQ-068, 069), 이전 설정 불러오기 (REQ-060).
 * 경과 시간(REQ-074) = 지금 - startedAt - pausedSeconds - (paused면 지금 - pausedAt). 멈춘 동안은 늘지 않는다 (REQ-119)
 */
public record MeetingDetailResponse(
        Long meetingId,
        Long workspaceId,
        Long folderId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String location,
        MeetingStatus status,
        MinutesProgress minutesStatus,
        PersonResponse owner,
        PersonResponse writer,
        List<PersonResponse> attendees,
        List<AgendaResponse> agendas,
        Long previousMeetingId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        boolean paused,
        LocalDateTime pausedAt,
        long pausedSeconds
) {
}
