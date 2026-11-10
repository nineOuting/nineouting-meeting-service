package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;

/**
 * REQ-143 쓰레기통에 있는 회의.
 *
 * @param hasMinutes 회의가 끝나 회의록이 있는지 (복구하면 회의록도 다시 보인다)
 * @param purgeAt    이 시각이 지나면 자동으로 영구 삭제된다
 */
public record TrashMeetingResponse(Long meetingId, String title, Long folderId, String folderName, LocalDateTime startAt,
                                   boolean hasMinutes, PersonResponse deletedBy, LocalDateTime deletedAt,
                                   LocalDateTime purgeAt) {
}
