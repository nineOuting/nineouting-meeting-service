package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** stt-service → meeting-service 회의 진행 알림 (docs/services/ai-integration.md 2.3) */
public record ProgressEventRequest(
        @NotNull @Pattern(regexp = "start|pause|resume|end", message = "event는 start, pause, resume, end 중 하나입니다")
        String event,
        @NotNull @PositiveOrZero Long atMs
) {
}
