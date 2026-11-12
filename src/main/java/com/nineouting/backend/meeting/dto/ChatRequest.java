package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** REQ-185 챗봇 질문 */
public record ChatRequest(
        @NotBlank(message = "질문을 입력해 주세요")
        @Size(max = 1000, message = "질문은 1,000자 이하로 입력해 주세요")
        String query
) {
}
