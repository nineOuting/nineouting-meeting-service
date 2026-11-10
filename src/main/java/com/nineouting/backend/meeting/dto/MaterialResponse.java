package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;

/** REQ-067 사전 자료. downloadUrl은 1시간 동안 쓸 수 있다 */
public record MaterialResponse(Long materialId, String fileName, String contentType, long size,
                               PersonResponse uploadedBy, LocalDateTime uploadedAt, String downloadUrl) {
}
