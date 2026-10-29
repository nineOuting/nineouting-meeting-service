package com.nineouting.backend.meeting.error;

import com.nineouting.backend.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * meeting-service 에러 코드 (docs/services/meeting-service.md 4.4).
 * 다른 서비스 연결 실패(503)는 client-common의 ClientErrorCode를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum MeetingErrorCode implements ErrorCode {

    FOLDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "폴더에 접근할 권한이 없습니다"),
    NOT_MEETING_OWNER(HttpStatus.FORBIDDEN, "회의를 만든 사람만 할 수 있습니다"),
    NOT_MEETING_ATTENDEE(HttpStatus.FORBIDDEN, "회의 참석자만 할 수 있습니다"),
    NOT_MEETING_WRITER(HttpStatus.FORBIDDEN, "회의록 작성자만 할 수 있습니다"),
    MEETING_NOT_EDITABLE(HttpStatus.CONFLICT, "시작 전인 회의만 수정·취소할 수 있습니다"),
    MEETING_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작했거나 끝난 회의입니다"),
    MEETING_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 회의가 아닙니다"),
    MEETING_TIME_INVALID(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 뒤여야 합니다"),
    ATTENDEE_NOT_FOLDER_MEMBER(HttpStatus.BAD_REQUEST, "폴더에 들어갈 수 없는 사람은 참석자로 고를 수 없습니다"),
    WRITER_NOT_ATTENDEE(HttpStatus.BAD_REQUEST, "회의록 작성자는 참석자 중에서 골라야 합니다"),
    PENDING_AGENDA_UNAVAILABLE(HttpStatus.CONFLICT, "이미 다른 회의에서 다루고 있는 안건입니다"),
    FOLDER_MOVE_INVALID(HttpStatus.BAD_REQUEST, "같은 워크스페이스의 폴더로만 옮길 수 있습니다"),
    CALENDAR_RANGE_INVALID(HttpStatus.BAD_REQUEST, "조회 기간은 42일까지입니다"),
    /** 음성 연결·회의방 입장권 공통. 이름은 stt-service와 맞춘 그대로 둔다 */
    MEETING_ALREADY_PAUSED(HttpStatus.CONFLICT, "이미 멈춘 회의입니다"),
    MEETING_NOT_PAUSED(HttpStatus.CONFLICT, "멈춘 회의가 아닙니다"),
    INVALID_STT_TICKET(HttpStatus.UNAUTHORIZED, "연결 입장권이 없거나 만료되었습니다"),
    TRANSCRIPT_NOT_EDITABLE(HttpStatus.CONFLICT, "아직 확정되지 않았거나 지운 줄은 고칠 수 없습니다"),
    END_NOT_REQUESTED(HttpStatus.CONFLICT, "회의 끝내기를 누르지 않았습니다"),
    MEETING_NOT_FINISHED(HttpStatus.CONFLICT, "끝난 회의만 할 수 있습니다");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
