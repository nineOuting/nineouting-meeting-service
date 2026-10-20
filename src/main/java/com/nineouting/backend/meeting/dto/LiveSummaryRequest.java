package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * ai-service → meeting-service 회의 중 요약 (docs/services/ai-integration.md 2.5,
 * ai-service docs/api/output-contract.md). 바뀔 때마다 통째로 온다.
 *
 * @param meetingId 문자열 (ai-service 형식). 주소의 회의 번호와 같아야 한다
 * @param version   보낼 때마다 커진다. 저장된 것보다 작거나 같으면 버린다
 * @param finalLine 회의가 끝난 뒤의 마지막 요약이면 true (JSON 이름은 final)
 */
public record LiveSummaryRequest(
        String schemaVersion,
        String meetingId,
        @NotNull Long version,
        @NotNull @PositiveOrZero Long atMs,
        @JsonProperty("final") @NotNull Boolean finalLine,
        @NotNull @Size(max = 200) List<@Valid @NotNull LiveSummaryItem> summary
) {
}
