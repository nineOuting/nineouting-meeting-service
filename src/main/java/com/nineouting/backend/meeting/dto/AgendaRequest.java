package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param decisionPoints  결정할 사항 (1~5개, 칸당 200자. 2026-10-23 결정)
 * @param pendingAgendaId 남은 안건에서 불러온 경우(REQ-068). 프론트는 남은 안건의 제목·결정할 사항을 채워서 보낸다
 */
public record AgendaRequest(
        @NotBlank(message = "안건을 입력해 주세요")
        @Size(max = 100, message = "안건은 100자 이하로 입력해 주세요")
        String title,
        @NotEmpty(message = "결정할 사항을 1개 이상 입력해 주세요")
        @Size(max = DecisionPoints.MAX_COUNT, message = "결정할 사항은 5개까지 입력할 수 있습니다")
        List<@NotBlank(message = "결정할 사항을 입력해 주세요")
             @Size(max = DecisionPoints.MAX_LENGTH, message = "결정할 사항은 200자 이하로 입력해 주세요") String> decisionPoints,
        Long pendingAgendaId
) {
    public AgendaRequest {
        title = title == null ? null : title.trim();
        decisionPoints = DecisionPoints.trim(decisionPoints);
    }
}
