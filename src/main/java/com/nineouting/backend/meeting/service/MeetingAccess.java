package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.FolderAccess;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회의 권한 규칙 (docs/requirements.md "권한", docs/services/meeting-service.md 3장).
 * 회의를 보려면 회의가 있는 폴더에 들어갈 수 있어야 한다. 폴더 권한은 workspace-service에 묻는다.
 */
@Component
@RequiredArgsConstructor
public class MeetingAccess {

    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final WorkspaceClient workspaceClient;

    /** 폴더에 들어갈 수 없으면 FOLDER_ACCESS_DENIED, 폴더가 없으면 NOT_FOUND */
    public FolderAccess requireFolderAccess(Long folderId, Long userId) {
        FolderAccess access = workspaceClient.access(folderId, userId);
        if (!access.allowed()) {
            throw new BusinessException(MeetingErrorCode.FOLDER_ACCESS_DENIED);
        }
        return access;
    }

    /** 회의가 있는 폴더에 들어갈 수 있는 사람만 회의를 볼 수 있다 */
    public Meeting requireViewable(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        requireFolderAccess(meeting.getFolderId(), userId);
        return meeting;
    }

    /** 수정·취소·폴더 이동: 회의를 만든 사람만 */
    public Meeting requireOwner(Long meetingId, Long userId) {
        Meeting meeting = requireViewable(meetingId, userId);
        if (!meeting.isOwnedBy(userId)) {
            throw new BusinessException(MeetingErrorCode.NOT_MEETING_OWNER);
        }
        return meeting;
    }

    /** 수정·취소: 만든 사람만, 시작 전에만 */
    public Meeting requireEditable(Long meetingId, Long userId) {
        Meeting meeting = requireOwner(meetingId, userId);
        if (!meeting.is(MeetingStatus.SCHEDULED)) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_EDITABLE);
        }
        return meeting;
    }

    public Meeting requireAttendee(Long meetingId, Long userId) {
        Meeting meeting = requireViewable(meetingId, userId);
        if (!attendeeRepository.existsByMeetingIdAndUserId(meetingId, userId)) {
            throw new BusinessException(MeetingErrorCode.NOT_MEETING_ATTENDEE);
        }
        return meeting;
    }

    /**
     * stt-service가 보내는 전사 텍스트·회의 진행 알림: 시작한 회의만.
     * 마무리 직후 STT가 마지막 줄을 확정해 보낼 수 있으므로 끝난 회의도 받는다.
     */
    public Meeting requireStarted(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!meeting.is(MeetingStatus.IN_PROGRESS) && !meeting.is(MeetingStatus.FINISHED)) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_IN_PROGRESS);
        }
        return meeting;
    }

    /** 회의 중 안건 추가·다음 회의로 넘기기·마무리: 회의록 작성자만, 진행 중에만 */
    public Meeting requireWriterInProgress(Long meetingId, Long userId) {
        Meeting meeting = requireViewable(meetingId, userId);
        if (!meeting.isWriter(userId)) {
            throw new BusinessException(MeetingErrorCode.NOT_MEETING_WRITER);
        }
        if (!meeting.is(MeetingStatus.IN_PROGRESS)) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_IN_PROGRESS);
        }
        return meeting;
    }
}
