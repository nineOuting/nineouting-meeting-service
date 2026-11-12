package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.ChatMessage;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-185, 186 챗봇 대화 한 개. role: USER(질문) / ASSISTANT(답).
 * 답의 status(answered, no_result), basis(sources, general, mixed), citations는 ai-service 응답 그대로다
 * (회의 중 챗봇 명세 5장). text에는 [n], [일반] 표시가 그대로 들어 있다.
 */
public record ChatMessageResponse(Long messageId, String role, String text, String status, String basis,
                                  JsonNode citations, LocalDateTime createdAt) {

    public static ChatMessageResponse of(ChatMessage message, JsonMapper jsonMapper) {
        JsonNode citations = message.getCitations() == null ? jsonMapper.createArrayNode()
                : jsonMapper.readTree(message.getCitations());
        return new ChatMessageResponse(message.getId(), message.getRole().name(), message.getText(), message.getStatus(),
                message.getBasis(), citations, message.getCreatedAt());
    }
}
