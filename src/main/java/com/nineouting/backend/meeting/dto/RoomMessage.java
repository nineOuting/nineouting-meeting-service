package com.nineouting.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 회의방 WebSocket으로 참석자 화면에 보내는 메시지 (docs/services/meeting-service.md 4.3.2).
 * <ul>
 *   <li>transcript: STT가 보낸 줄이 반영됨 (말하는 중인 줄도 포함, 같은 seq는 통째로 바꾼다)</li>
 *   <li>transcriptEdited: 회의록 작성자가 고침 (REQ-112)</li>
 *   <li>transcriptDeleted: 회의록 작성자가 지움 (seq, rev)</li>
 *   <li>paused / resumed: 회의록 작성자가 회의를 멈춤·재개 (REQ-119)</li>
 *   <li>finished: 회의가 마무리됨. 서버가 연결을 닫는다</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomMessage(String type, TranscriptLineResponse line, Integer seq, Integer rev) {

    public static RoomMessage transcript(TranscriptLineResponse line) {
        return new RoomMessage("transcript", line, null, null);
    }

    public static RoomMessage transcriptEdited(TranscriptLineResponse line) {
        return new RoomMessage("transcriptEdited", line, null, null);
    }

    public static RoomMessage transcriptDeleted(int seq, int rev) {
        return new RoomMessage("transcriptDeleted", null, seq, rev);
    }

    public static RoomMessage paused() {
        return new RoomMessage("paused", null, null, null);
    }

    public static RoomMessage resumed() {
        return new RoomMessage("resumed", null, null, null);
    }

    public static RoomMessage finished() {
        return new RoomMessage("finished", null, null, null);
    }
}
