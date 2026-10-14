package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import java.time.LocalDateTime;

/** 폴더 내 회의 목록 항목 (REQ-046) */
public record MeetingSummaryResponse(Long meetingId, String title, LocalDateTime startAt, LocalDateTime endAt,
                                     MeetingStatus status) {

    public static MeetingSummaryResponse from(Meeting meeting) {
        return new MeetingSummaryResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(), meeting.getEndAt(),
                meeting.getStatus());
    }
}
