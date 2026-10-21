package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param pendingAgendaId 남은 안건에서 불러온 경우(REQ-068). 프론트는 남은 안건의 제목·결정할 사항을 채워서 보낸다
 */
public record AgendaRequest(
        @NotBlank(message = "안건을 입력해 주세요")
        @Size(max = 100, message = "안건은 100자 이하로 입력해 주세요")
        String title,
        @NotBlank(message = "결정할 사항을 입력해 주세요")
        @Size(max = 500, message = "결정할 사항은 500자 이하로 입력해 주세요")
        String decisionPoint,
        Long pendingAgendaId
) {
    public AgendaRequest {
        title = title == null ? null : title.trim();
        decisionPoint = decisionPoint == null ? null : decisionPoint.trim();
    }
}
