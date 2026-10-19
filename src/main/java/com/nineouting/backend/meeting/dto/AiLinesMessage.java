package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** meeting-service → ai-service 줄 묶음 (docs/services/ai-integration.md 2.4). meetingId는 ai-service가 문자열로 받는다 */
public record AiLinesMessage(String meetingId, int batch, List<Line> lines, List<Edit> edits) {

    /** 새로 확정된 줄. manual: 사용자가 직접 추가한 줄 (아직 요구사항에 없어 항상 false) */
    public record Line(int seq, String speakerLabel, long startMs, Long endMs, String text, String kind,
                       boolean lowConfidence, boolean recovered, boolean manual) {
    }

    /** 이미 넘긴 줄의 변경. 수정이면 text, 삭제면 deleted만 담는다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Edit(int seq, int rev, String text, Boolean deleted) {
    }
}
