package com.nineouting.backend.meeting.domain;

/** 회의록 진행 상태 (REQ-046 목록의 "검토 중·확정"). minutes-service가 알려 준다 */
public enum MinutesProgress {
    /** 검토 중 (minutes.review-requested) */
    REVIEWING,
    /** 확정 (minutes.confirmed) */
    CONFIRMED
}
