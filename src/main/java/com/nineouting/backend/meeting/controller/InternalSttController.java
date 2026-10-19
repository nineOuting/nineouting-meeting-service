package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.ProgressEventRequest;
import com.nineouting.backend.meeting.dto.SttTicketOwnerResponse;
import com.nineouting.backend.meeting.dto.TranscriptSegmentsRequest;
import com.nineouting.backend.meeting.service.ProgressEventService;
import com.nineouting.backend.meeting.service.SttTicketService;
import com.nineouting.backend.meeting.service.TranscriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * stt-service가 클러스터 안에서 부르는 주소 (docs/services/ai-integration.md 2장).
 * Gateway가 /internal을 외부로 열지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class InternalSttController {

    private final SttTicketService sttTicketService;
    private final TranscriptService transcriptService;
    private final ProgressEventService progressEventService;

    /** 2.1 음성 연결 입장권 확인. 한 번 쓰면 지운다. 없거나 만료면 401 INVALID_STT_TICKET */
    @PostMapping("/internal/stt-tickets/{ticket}/consume")
    public ApiResponse<SttTicketOwnerResponse> consumeTicket(@PathVariable String ticket) {
        return ApiResponse.ok(sttTicketService.consume(ticket));
    }

    /** 2.2 전사 텍스트 저장 */
    @PostMapping("/internal/meetings/{meetingId}/transcripts")
    public ResponseEntity<Void> saveTranscripts(@PathVariable Long meetingId,
                                                @Valid @RequestBody TranscriptSegmentsRequest request) {
        transcriptService.saveFromStt(meetingId, request.segments());
        return ResponseEntity.noContent().build();
    }

    /** 2.3 회의 진행 알림 */
    @PostMapping("/internal/meetings/{meetingId}/events")
    public ResponseEntity<Void> recordEvent(@PathVariable Long meetingId,
                                            @Valid @RequestBody ProgressEventRequest request) {
        progressEventService.record(meetingId, request.event(), request.atMs());
        return ResponseEntity.noContent().build();
    }
}
