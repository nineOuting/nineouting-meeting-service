package com.nineouting.backend.meeting.domain;

/**
 * 안건별 논의 현황 (REQ-086). 만들 때는 NOT_STARTED, 회의 중에는 ai-service 안건 보드가 보내는 값으로 바꾼다.
 * DECISION_CHECK: 결정처럼 보이지만 AI가 확신하지 못함 (결정 확인 필요, ai-service agenda-status-contract.md)
 */
public enum AgendaStatus {
    NOT_STARTED,
    DISCUSSING,
    DECIDED,
    UNDECIDED,
    DECISION_CHECK
}
