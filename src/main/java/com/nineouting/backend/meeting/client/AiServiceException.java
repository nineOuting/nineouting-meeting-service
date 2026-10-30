package com.nineouting.backend.meeting.client;

/** ai-service가 응답하지 않거나 거절함. 보내던 것은 남겨 두고 다음 주기에 다시 보낸다 (REQ-082) */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
