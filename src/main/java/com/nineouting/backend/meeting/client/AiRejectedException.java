package com.nineouting.backend.meeting.client;

/**
 * ai-service가 요청을 거절함 (408·409·429를 뺀 4xx, 예: 400 INVALID_INPUT).
 * 같은 내용을 다시 보내도 결과가 같으므로 다시 보내지 않고 기록만 한다. 다시 보내면 그 회의의 뒤 묶음이 모두 막힌다.
 */
public class AiRejectedException extends AiServiceException {

    public AiRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
