package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회의 중 안건에 결정할 사항 하나 더하기 (회의록 작성자, 디자인 Version02 Live 결정할 사항 +) */
public record AddDecisionPointRequest(
        @NotBlank(message = "결정할 사항을 입력해 주세요")
        @Size(max = DecisionPoints.MAX_LENGTH, message = "결정할 사항은 200자 이하로 입력해 주세요")
        String content
) {
    public AddDecisionPointRequest {
        content = content == null ? null : content.trim();
    }
}
