package com.nineouting.backend.meeting.event;

import com.nineouting.backend.common.event.meeting.MeetingMoved;

/**
 * 회의 쓰레기통 이벤트 (REQ-166, REQ-143). minutes-service가 회의록을 숨기거나(쓰레기통) 다시 보이거나(복구) 지운다(영구 삭제).
 * <p>
 * 아직 nineouting-common에 없어 여기와 minutes-service에 같은 모양으로 둔다. 새 토픽을 만들지 않으려고
 * minutes-service가 이미 구독하는 meeting.moved 토픽으로 보낸다 (inbox는 eventType으로 처리기를 고른다).
 * 다음 common 릴리스 때 event-contract로 옮긴다.
 */
public final class MeetingTrashEvents {

    public static final String TOPIC = MeetingMoved.TOPIC;

    private MeetingTrashEvents() {
    }

    public record MeetingTrashed(Long meetingId, Long workspaceId, Long folderId) {
        public static final String TYPE = "MeetingTrashed";
    }

    public record MeetingRestored(Long meetingId, Long workspaceId, Long folderId) {
        public static final String TYPE = "MeetingRestored";
    }

    public record MeetingPurged(Long meetingId, Long workspaceId) {
        public static final String TYPE = "MeetingPurged";
    }
}
