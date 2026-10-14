package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.MinutesProgress;
import java.time.LocalDateTime;

/**
 * 홈 캘린더 항목 (REQ-018~020). 프론트는 status로 예정·지난 회의 색과 이동할 화면(대기 화면 / 회의록)을 정한다.
 */
public record CalendarMeetingResponse(
        Long meetingId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        MeetingStatus status,
        MinutesProgress minutesStatus,
        Long workspaceId,
        String workspaceName,
        Long folderId,
        String folderName
) {
}
