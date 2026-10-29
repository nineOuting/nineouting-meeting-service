package com.nineouting.backend.meeting.dto;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * minutes-service가 AI 회의록 초안(REQ-101~103)을 요청할 때 쓰는 재료 (ai-service minutes-draft-contract.md 5.1·5.2).
 *
 * @param ready   회의가 끝난 뒤의 오늘 회의 요약(final)이 있으면 true
 * @param summary {agendas[...]} (todaySummary 그대로, 점검에서 고친 내용 포함). 없으면 빈 agendas
 */
public record DraftSourceResponse(Long meetingId, boolean ready, List<Agenda> agendas, JsonNode summary) {

    /** @param decisionPoints 결정할 사항 목록 (안건 목록 보내기와 같은 모양, 초안의 미결·결정 확인 필요 줄) */
    public record Agenda(Long agendaId, String title, List<DecisionPoint> decisionPoints) {
    }

    /** @param position 1부터 */
    public record DecisionPoint(Long decisionPointId, int position, String text) {
    }
}
