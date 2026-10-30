package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** stt-service → meeting-service 전사 텍스트 (docs/services/ai-integration.md 2.2) */
public record TranscriptSegmentsRequest(
        @NotEmpty(message = "segments가 비어 있습니다")
        @Size(max = 500, message = "한 번에 500줄까지 보낼 수 있습니다")
        List<@Valid @NotNull Segment> segments
) {

    /**
     * @param seq       1 이상 (0 이하는 사용자가 직접 추가하는 줄에 남겨 둔다)
     * @param rev       같은 seq의 갱신 번호. 더 큰 것만 반영한다
     * @param finalLine JSON 이름은 final
     * @param deleted   true면 STT가 확정하지 않고 버린 말하는 중 줄 (나머지 칸은 마지막으로 보낸 값 그대로, 쓰지 않는다)
     */
    public record Segment(
            @NotNull @Min(value = 1, message = "seq는 1 이상입니다") Integer seq,
            @NotNull @Min(value = 1, message = "rev는 1 이상입니다") Integer rev,
            @Size(max = 50) String speakerLabel,
            @NotNull @Size(max = 5000) String text,
            @NotNull @PositiveOrZero Long startMs,
            @PositiveOrZero Long endMs,
            @JsonProperty("final") @NotNull Boolean finalLine,
            Boolean lowConfidence,
            @Pattern(regexp = "backchannel|filler", message = "kind는 backchannel, filler 또는 null입니다") String kind,
            Boolean recovered,
            Boolean deleted
    ) {
    }
}
