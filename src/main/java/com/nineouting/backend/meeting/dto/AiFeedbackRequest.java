package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.AiFeedback;
import jakarta.validation.constraints.NotNull;

/** REQ-172 AI 결과 피드백 */
public record AiFeedbackRequest(
        @NotNull(message = "피드백을 남길 자리를 골라 주세요")
        AiFeedback.Target target,
        @NotNull(message = "피드백을 골라 주세요")
        AiFeedback.Rating rating
) {
}
