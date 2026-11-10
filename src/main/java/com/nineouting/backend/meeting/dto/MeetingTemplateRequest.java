package com.nineouting.backend.meeting.dto;

/**
 * REQ-123 회의록 양식 고르기 (minutes-service의 양식 ID). null이면 고른 양식을 뺀다.
 * 양식이 있는지는 minutes-service가 회의록을 만들 때 확인한다 (없거나 볼 수 없으면 양식 없이 만든다)
 */
public record MeetingTemplateRequest(Long templateId) {
}
