package com.nineouting.backend.meeting.domain;

/**
 * ai-service로 보내는 사람의 요청 종류와 주소. 재시작 복구 때 이 순서대로 다시 보낸다
 * (ai-service docs/api/agenda-leftover-contract.md, agenda-contract.md 재시작 복구).
 */
public enum AgendaEditKind {
    /** 줄의 안건 분류 고치기 (REQ-089) */
    CORRECTION("agenda-corrections", "corrections"),
    /** 요약 문장 고치기·확인 (REQ-085) */
    SUMMARY("agenda-summary-edits", "edits"),
    /** 의견·할 일 고치기·확인 (REQ-087, 097) */
    ITEM("agenda-item-edits", "edits"),
    /** 결정 내용 고치기 (REQ-126) */
    DECISION("decision-edits", "edits"),
    /** 새 안건 제안·주제 이탈 알림 무시 (REQ-091~092). 하나씩 보낸다 */
    OFF_TOPIC("off-topic-actions", null),
    /** 회의 끝내기·이어서 논의하기 (REQ-093, 095). 하나씩 보낸다 */
    END_REQUEST("end-requests", null);

    private final String path;
    /** 여러 개를 한 요청에 담는 배열 이름. null이면 항목 하나가 곧 본문이다 */
    private final String arrayName;

    AgendaEditKind(String path, String arrayName) {
        this.path = path;
        this.arrayName = arrayName;
    }

    public String path() {
        return path;
    }

    public String arrayName() {
        return arrayName;
    }
}
