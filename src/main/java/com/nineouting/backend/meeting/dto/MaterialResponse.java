package com.nineouting.backend.meeting.dto;

import java.time.LocalDateTime;

/**
 * REQ-067 사전 자료. downloadUrl은 1시간 동안 쓸 수 있다.
 * searchStatus: REQ-187 챗봇 검색 상태 (PREPARING, READY, UNREADABLE, TOO_LARGE, FAILED). 챗봇이 꺼져 있으면 null
 */
public record MaterialResponse(Long materialId, String fileName, String contentType, long size,
                               PersonResponse uploadedBy, LocalDateTime uploadedAt, String downloadUrl,
                               String searchStatus) {
}
