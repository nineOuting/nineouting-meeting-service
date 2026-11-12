package com.nineouting.backend.meeting.dto;

/**
 * REQ-185 질문 하나의 결과. status: answered / no_result / failed.
 * answered, no_result면 저장한 질문과 답을 준다. failed면 저장하지 않고(다시 물을 수 있게) question, answer가 null이다
 * (errorCode: llm_failed, llm_timeout, unavailable).
 */
public record ChatAnswerResponse(String status, String errorCode, ChatMessageResponse question,
                                 ChatMessageResponse answer) {

    public static ChatAnswerResponse failed(String errorCode) {
        return new ChatAnswerResponse("failed", errorCode, null, null);
    }
}
