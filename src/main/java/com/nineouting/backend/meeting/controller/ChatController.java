package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.ChatAnswerResponse;
import com.nineouting.backend.meeting.dto.ChatMessageResponse;
import com.nineouting.backend.meeting.dto.ChatRequest;
import com.nineouting.backend.meeting.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REQ-185, 186 회의 중 챗봇: 내 대화 보기, 질문하기 (답이 나올 때까지 최대 60초) */
@RestController
@RequestMapping("/api/meetings/{meetingId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ApiResponse<List<ChatMessageResponse>> history(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable Long meetingId) {
        return ApiResponse.ok(chatService.history(meetingId, user.userId()));
    }

    @PostMapping
    public ApiResponse<ChatAnswerResponse> ask(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                               @Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.ask(meetingId, user.userId(), request.query()));
    }
}
