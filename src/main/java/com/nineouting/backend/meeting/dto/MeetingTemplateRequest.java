package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.Size;

/**
 * REQ-123 회의록 양식 고르기 (minutes-service의 양식 ID). null이면 고른 양식을 뺀다.
 * 양식이 있는지는 minutes-service가 회의록을 만들 때 확인한다 (없거나 볼 수 없으면 양식 없이 만든다)
 *
 * @param templateName 고른 양식의 이름. 다른 참석자의 대기 화면에 보여 준다 (SCRUM-419). 없으면 응답의 templateName이 null
 */
public record MeetingTemplateRequest(
        Long templateId,
        @Size(max = 100, message = "양식 이름은 100자 이하로 보내 주세요")
        String templateName
) {
}
