package com.nineouting.backend.meeting.dto;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * minutes-service가 AI 회의록 초안(REQ-101~103)을 요청할 때 쓰는 재료 (ai-service minutes-draft-contract.md 5.1·5.2).
 *
 * @param ready   회의가 끝난 뒤의 오늘 회의 요약(final)이 있으면 true
 * @param summary {agendas[...]} (todaySummary 그대로, 점검에서 고친 내용 포함). 없으면 빈 agendas
 * @param transcripts 확정된 전사 줄 (지운 줄 제외, 작성자가 고친 글). ai-service가 STT 오인식 교정의 표기 출처로만 쓴다 (5.10, 7.7)
 */
public record DraftSourceResponse(Long meetingId, boolean ready, List<Agenda> agendas, JsonNode summary,
                                  List<Transcript> transcripts) {

    /** @param decisionPoints 결정할 사항 목록 (안건 목록 보내기와 같은 모양, 초안의 미결·결정 확인 필요 줄) */
    public record Agenda(Long agendaId, String title, List<DecisionPoint> decisionPoints) {
    }

    /** @param position 1부터 */
    public record DecisionPoint(Long decisionPointId, int position, String text) {
    }

    /** ai-service가 읽는 칸만 담는다 */
    public record Transcript(int seq, String text, boolean lowConfidence) {
    }
}
