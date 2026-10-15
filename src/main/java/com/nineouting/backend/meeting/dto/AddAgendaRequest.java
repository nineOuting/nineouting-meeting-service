package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회의 도중 안건 추가 (REQ-114) */
public record AddAgendaRequest(
        @NotBlank(message = "안건을 입력해 주세요")
        @Size(max = 100, message = "안건은 100자 이하로 입력해 주세요")
        String title,
        @NotBlank(message = "결정할 사항을 입력해 주세요")
        @Size(max = 500, message = "결정할 사항은 500자 이하로 입력해 주세요")
        String decisionPoint
) {
    public AddAgendaRequest {
        title = title == null ? null : title.trim();
        decisionPoint = decisionPoint == null ? null : decisionPoint.trim();
    }
}
