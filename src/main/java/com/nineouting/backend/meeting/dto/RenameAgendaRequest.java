package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** REQ-173 회의 중 안건 이름 고치기 (회의록 작성자) */
public record RenameAgendaRequest(
        @NotBlank(message = "안건을 입력해 주세요")
        @Size(max = 100, message = "안건은 100자 이하로 입력해 주세요")
        String title
) {
    public RenameAgendaRequest {
        title = title == null ? null : title.trim();
    }
}
