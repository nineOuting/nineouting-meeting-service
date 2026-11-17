package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;

/**
 * REQ-196 홈 내 할 일. 끝난 회의에서 나에게 배정된 할 일 중 아직 완료하지 않은 것.
 *
 * @param itemId  안건 보드의 할 일 번호 (완료할 때 그대로 보낸다)
 * @param dueText 기한 (AI가 들은 그대로, 없으면 null)
 */
public record MyTodoResponse(Long meetingId, String itemId, String task, String dueText, String meetingTitle,
                             LocalDateTime meetingStartAt, Long workspaceId, Long folderId, String folderName) {
}
