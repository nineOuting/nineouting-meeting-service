package com.nineouting.backend.meeting.dto;

/**
 * REQ-067 업로드 주소. 브라우저는 uploadUrl로 PUT(Content-Type은 요청한 값 그대로)한 뒤
 * POST /api/meetings/{meetingId}/materials/{materialId}/complete 를 부른다
 */
public record MaterialUploadResponse(Long materialId, String uploadUrl, String contentType) {
}
