package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 결정할 사항 상태 정하기 (ai-service agenda-status-contract.md 7.5).
 * status: DECIDED(결정) / UNDECIDED(미결) / AUTO(사용자가 정한 상태를 풀고 AI 판정으로)
 */
public record DecisionStatusRequest(
        @NotBlank(message = "status를 보내 주세요")
        String status
) {
}
