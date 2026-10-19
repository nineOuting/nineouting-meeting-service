package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.EditTranscriptRequest;
import com.nineouting.backend.meeting.dto.SttTicketResponse;
import com.nineouting.backend.meeting.dto.TranscriptLineResponse;
import com.nineouting.backend.meeting.service.SttTicketService;
import com.nineouting.backend.meeting.service.TranscriptService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 회의 중 음성 연결 입장권과 전사 텍스트 (REQ-072, 112) */
@RestController
@RequiredArgsConstructor
public class TranscriptController {

    private final SttTicketService sttTicketService;
    private final TranscriptService transcriptService;

    /** 음성 연결(stt-service WebSocket)에 붙일 30초짜리 1회용 입장권. 회의록 작성자, 진행 중 */
    @PostMapping("/api/meetings/{meetingId}/stt-ticket")
    public ApiResponse<SttTicketResponse> sttTicket(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        return ApiResponse.ok(sttTicketService.issue(meetingId, user.userId()));
    }

    /** REQ-072. 참석자. startMs 순 */
    @GetMapping("/api/meetings/{meetingId}/transcripts")
    public ApiResponse<List<TranscriptLineResponse>> list(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable Long meetingId) {
        return ApiResponse.ok(transcriptService.list(meetingId, user.userId()));
    }

    /** REQ-112. 회의록 작성자, 진행 중 */
    @PatchMapping("/api/meetings/{meetingId}/transcripts/{seq}")
    public ApiResponse<TranscriptLineResponse> edit(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                                    @PathVariable int seq,
                                                    @Valid @RequestBody EditTranscriptRequest request) {
        return ApiResponse.ok(transcriptService.edit(meetingId, user.userId(), seq, request.text()));
    }

    /** REQ-112. 회의록 작성자, 진행 중 */
    @DeleteMapping("/api/meetings/{meetingId}/transcripts/{seq}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                    @PathVariable int seq) {
        transcriptService.delete(meetingId, user.userId(), seq);
        return ApiResponse.ok();
    }
}
