package com.nineouting.backend.meeting.domain;

/** 폴더의 남은 안건 상태 (REQ-051~052) */
public enum PendingAgendaStatus {
    /** 대기: 회의에서 다음 회의로 넘김 */
    WAITING,
    /** 진행 중: 다음 회의에 불러옴 */
    IN_PROGRESS,
    /** 처리됨: 불러온 회의가 마무리됐는데 다시 넘기지 않음 */
    DONE
}
