package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * 오늘 회의 요약 (REQ-097, ai-service agenda-today-summary-contract.md).
 *
 * @param finalSummary 회의가 끝난 뒤의 마지막 값이면 true (점검에서 고친 내용 포함). false면 회의 끝내기를 누른 뒤의 지금 값
 * @param todaySummary {agendas[...]} 모양. 아직 없으면 null
 */
public record TodaySummaryResponse(@JsonProperty("final") boolean finalSummary, JsonNode todaySummary) {
}
