package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 폴더의 남은 안건 (REQ-051~052).
 *
 * @param fromMeetingTitle 마지막으로 넘긴 회의 (이월된 회의)
 * @param carriedAt        이월 날짜
 * @param takenMeetingId   진행 중이면 불러간 회의
 */
public record PendingAgendaResponse(
        Long pendingAgendaId,
        String title,
        List<String> decisionPoints,
        PendingAgendaStatus status,
        Long fromMeetingId,
        String fromMeetingTitle,
        LocalDateTime carriedAt,
        Long takenMeetingId
) {
}
