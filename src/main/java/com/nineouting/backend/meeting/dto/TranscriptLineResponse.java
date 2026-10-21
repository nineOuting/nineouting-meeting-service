package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nineouting.backend.meeting.domain.TranscriptLine;

/** 전사 텍스트 한 줄 (REQ-079). 화면은 startMs 순으로 받는다 */
public record TranscriptLineResponse(int seq, int rev, String speakerLabel, String text, long startMs, Long endMs,
                                     @JsonProperty("final") boolean finalLine, boolean lowConfidence, String kind,
                                     boolean recovered) {

    public static TranscriptLineResponse from(TranscriptLine line) {
        return new TranscriptLineResponse(line.getSeq(), line.getRev(), line.getSpeakerLabel(), line.getText(),
                line.getStartMs(), line.getEndMs(), line.isFinalLine(), line.isLowConfidence(), line.getKind(),
                line.isRecovered());
    }
}
