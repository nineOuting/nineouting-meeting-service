package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 결정 내용 고치기 (REQ-126). evidenceSeqs는 보드에서 받은 근거를 그대로 (없으면 빈 목록) */
public record DecisionEditRequest(
        @NotBlank(message = "결정 내용을 입력해 주세요")
        @Size(max = 500, message = "결정 내용은 500자 이하로 입력해 주세요")
        String decisionText,
        List<Integer> evidenceSeqs
) {
}
