package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.AgendaDecisionPoint;

/** 안건의 결정할 사항 한 개. decisionPointId는 안건 보드(ai-service)가 가리키는 번호 */
public record DecisionPointResponse(Long decisionPointId, int position, String text) {
    public static DecisionPointResponse from(AgendaDecisionPoint decisionPoint) {
        return new DecisionPointResponse(decisionPoint.getId(), decisionPoint.getPosition(), decisionPoint.getContent());
    }
}
