package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 회의방 WebSocket으로 참석자 화면에 보내는 메시지 (docs/services/meeting-service.md 4.3.2).
 * <ul>
 *   <li>transcript: STT가 보낸 줄이 반영됨 (말하는 중인 줄도 포함, 같은 seq는 통째로 바꾼다)</li>
 *   <li>transcriptEdited: 회의록 작성자가 고침 (REQ-083)</li>
 *   <li>transcriptDeleted: 회의록 작성자가 지움 (seq, rev)</li>
 *   <li>paused / resumed: 회의록 작성자가 회의를 멈춤·재개 (REQ-084)</li>
 *   <li>liveSummary: ai-service가 보낸 회의 중 요약이 바뀜 (REQ-085). 늦게 들어오면 GET /live-summary로 지금 것을 본다</li>
 *   <li>finished: 회의가 마무리됨. 서버가 연결을 닫는다</li>
 *   <li>aiStatus: ai-service가 멈추거나(available=false) 돌아옴 → 화면에 "AI 일시 중단" 표시 (REQ-082).
 *       회의방에 들어오면 지금 상태를 한 번 보낸다</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomMessage(String type, TranscriptLineResponse line, Integer seq, Integer rev, Boolean available,
                          LiveSummaryResponse liveSummary) {

    public static RoomMessage transcript(TranscriptLineResponse line) {
        return new RoomMessage("transcript", line, null, null, null, null);
    }

    public static RoomMessage transcriptEdited(TranscriptLineResponse line) {
        return new RoomMessage("transcriptEdited", line, null, null, null, null);
    }

    public static RoomMessage transcriptDeleted(int seq, int rev) {
        return new RoomMessage("transcriptDeleted", null, seq, rev, null, null);
    }

    public static RoomMessage paused() {
        return new RoomMessage("paused", null, null, null, null, null);
    }

    public static RoomMessage resumed() {
        return new RoomMessage("resumed", null, null, null, null, null);
    }

    public static RoomMessage aiStatus(boolean available) {
        return new RoomMessage("aiStatus", null, null, null, available, null);
    }

    public static RoomMessage liveSummary(LiveSummaryResponse summary) {
        return new RoomMessage("liveSummary", null, null, null, null, summary);
    }

    public static RoomMessage finished() {
        return new RoomMessage("finished", null, null, null, null, null);
    }
}
