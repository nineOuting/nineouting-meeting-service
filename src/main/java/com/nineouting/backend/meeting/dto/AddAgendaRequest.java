package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 회의 도중 안건 추가 (REQ-090). 결정할 사항은 1개 이상 (2026-10-23 결정) */
public record AddAgendaRequest(
        @NotBlank(message = "안건을 입력해 주세요")
        @Size(max = 100, message = "안건은 100자 이하로 입력해 주세요")
        String title,
        @NotEmpty(message = "결정할 사항을 1개 이상 입력해 주세요")
        @Size(max = DecisionPoints.MAX_COUNT, message = "결정할 사항은 5개까지 입력할 수 있습니다")
        List<@NotBlank(message = "결정할 사항을 입력해 주세요")
             @Size(max = DecisionPoints.MAX_LENGTH, message = "결정할 사항은 200자 이하로 입력해 주세요") String> decisionPoints
) {
    public AddAgendaRequest {
        title = title == null ? null : title.trim();
        decisionPoints = DecisionPoints.trim(decisionPoints);
    }
}
