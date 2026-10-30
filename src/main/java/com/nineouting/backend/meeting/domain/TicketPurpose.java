package com.nineouting.backend.meeting.domain;

/** 입장권 용도 */
public enum TicketPurpose {
    /** 브라우저 → stt-service 음성 연결. 회의록 작성자만 */
    STT,
    /** 브라우저 → meeting-service 회의방 (전사 텍스트 실시간 중계). 참석자 누구나 */
    ROOM
}
