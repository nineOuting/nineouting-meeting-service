package com.nineouting.backend.meeting.domain;

/**
 * 회의 상태. 화면의 "검토 중·확정"은 회의록 상태라서 minutes-service를 만들 때 붙인다.
 */
public enum MeetingStatus {
    /** 예정 */
    SCHEDULED,
    /** 진행 중 (REQ-070) */
    IN_PROGRESS,
    /** 끝남 (REQ-087 마무리) */
    FINISHED,
    /** 취소 (REQ-112). 목록·캘린더에서 숨긴다 */
    CANCELLED
}
