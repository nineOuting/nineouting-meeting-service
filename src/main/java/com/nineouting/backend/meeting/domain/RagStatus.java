package com.nineouting.backend.meeting.domain;

/** 사전 자료를 ai-service 검색용 저장소에 넣은 상태 (docs/services/rag-chatbot.md 2장) */
public enum RagStatus {
    /** 보낼 차례 (처음, 다시 보내기) */
    PENDING,
    /** ai-service가 접수함. 완료 콜백을 기다린다 */
    ACCEPTED,
    /** 검색 가능 */
    READY,
    /** 실패. 다시 보내지 않는다 (ragErrorCode) */
    FAILED
}
