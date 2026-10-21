package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회의 중 전사 텍스트 수정 (REQ-083) */
public record EditTranscriptRequest(
        @NotBlank(message = "내용을 입력해 주세요")
        @Size(max = 5000, message = "한 줄은 5000자 이하로 입력해 주세요")
        String text
) {

    public EditTranscriptRequest {
        text = text == null ? null : text.trim();
    }
}
