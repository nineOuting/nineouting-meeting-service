package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** 회의 중 요약 (참석자 화면). 아직 없으면 API는 data: null */
public record LiveSummaryResponse(long version, long atMs, @JsonProperty("final") boolean finalSummary,
                                  List<LiveSummaryItem> summary, LocalDateTime receivedAt) {
}
