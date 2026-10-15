package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.MinutesProgress;
import java.time.LocalDateTime;

/**
 * 폴더 내 회의 목록 항목 (REQ-046).
 *
 * @param minutesStatus 회의록 검토 중·확정. 검토 요청 전이면 null
 */
public record MeetingSummaryResponse(Long meetingId, String title, LocalDateTime startAt, LocalDateTime endAt,
                                     MeetingStatus status, MinutesProgress minutesStatus) {

    public static MeetingSummaryResponse from(Meeting meeting) {
        return new MeetingSummaryResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(), meeting.getEndAt(),
                meeting.getStatus(), meeting.getMinutesStatus());
    }
}
