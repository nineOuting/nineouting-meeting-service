package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.MinutesProgress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 폴더 내 회의 목록 항목 (REQ-048: 회의명, 날짜, 상태, 작성자, 참여자).
 *
 * @param minutesStatus 회의록 검토 중·확정. 검토 요청 전이면 null
 * @param writer        회의록 작성자
 */
public record MeetingSummaryResponse(Long meetingId, String title, LocalDateTime startAt, LocalDateTime endAt,
                                     MeetingStatus status, MinutesProgress minutesStatus, PersonResponse writer,
                                     List<PersonResponse> attendees) {

    public static MeetingSummaryResponse of(Meeting meeting, PersonResponse writer, List<PersonResponse> attendees) {
        return new MeetingSummaryResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(), meeting.getEndAt(),
                meeting.getStatus(), meeting.getMinutesStatus(), writer, attendees);
    }
}
