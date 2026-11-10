package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import com.nineouting.backend.meeting.dto.PersonResponse;
import com.nineouting.backend.meeting.dto.TrashMeetingResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.event.MeetingTrashEvents.MeetingPurged;
import com.nineouting.backend.meeting.event.MeetingTrashEvents.MeetingRestored;
import com.nineouting.backend.meeting.event.MeetingTrashEvents.MeetingTrashed;
import com.nineouting.backend.meeting.event.MeetingTrashEvents;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.PendingAgendaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회의 쓰레기통 (REQ-166 회의 삭제, REQ-143 삭제 목록). 폴더 쓰레기통(workspace-service)과 같은 규칙이다.
 * <ul>
 *   <li>회의를 만든 사람이 지운다. 진행 중인 회의는 먼저 끝내야 한다</li>
 *   <li>지운 사람과 회의를 만든 사람이 복구하거나 영구 삭제한다 ([되돌리기]도 복구다)</li>
 *   <li>3개월이 지나면 자동으로 영구 삭제한다</li>
 * </ul>
 * 회의록은 minutes-service가 이벤트를 받아 숨기거나 다시 보이거나 지운다.
 */
@Slf4j
@Service
public class MeetingTrashService {

    private final MeetingRepository meetingRepository;
    private final PendingAgendaRepository pendingAgendaRepository;
    private final MeetingAccess access;
    private final MeetingPurger purger;
    private final WorkspaceClient workspaceClient;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final Duration retention;

    public MeetingTrashService(MeetingRepository meetingRepository, PendingAgendaRepository pendingAgendaRepository,
                               MeetingAccess access, MeetingPurger purger, WorkspaceClient workspaceClient,
                               UserClient userClient, OutboxEventWriter outboxEventWriter,
                               PlatformTransactionManager transactionManager, Clock clock,
                               @Value("${nineouting.meeting.trash-retention:90d}") Duration retention) {
        this.meetingRepository = meetingRepository;
        this.pendingAgendaRepository = pendingAgendaRepository;
        this.access = access;
        this.purger = purger;
        this.workspaceClient = workspaceClient;
        this.userClient = userClient;
        this.outboxEventWriter = outboxEventWriter;
        this.transactionManager = transactionManager;
        this.clock = clock;
        this.retention = retention;
    }

    /** REQ-166 회의 삭제. 시작 전 회의가 불러온 남은 안건은 다시 대기로 돌린다 (회의 취소와 같다) */
    @Transactional
    public void trash(Long meetingId, Long userId) {
        Meeting meeting = access.requireOwner(meetingId, userId);
        if (meeting.is(MeetingStatus.IN_PROGRESS)) {
            throw new BusinessException(MeetingErrorCode.MEETING_IN_PROGRESS_UNDELETABLE);
        }
        meeting.trash(userId, now());
        pendingAgendaRepository.findByTakenMeetingIdAndStatus(meetingId, PendingAgendaStatus.IN_PROGRESS)
                .forEach(PendingAgenda::release);
        outboxEventWriter.append(MeetingTrashEvents.TOPIC, meetingId.toString(), MeetingTrashed.TYPE,
                new MeetingTrashed(meetingId, meeting.getWorkspaceId(), meeting.getFolderId()));
    }

    /** REQ-143 복구, REQ-166 [되돌리기]. 폴더에 아직 들어갈 수 있어야 한다 */
    @Transactional
    public void restore(Long meetingId, Long userId) {
        Meeting meeting = requireTrashedManageable(meetingId, userId);
        access.requireFolderAccess(meeting.getFolderId(), userId);
        meeting.restore();
        outboxEventWriter.append(MeetingTrashEvents.TOPIC, meetingId.toString(), MeetingRestored.TYPE,
                new MeetingRestored(meetingId, meeting.getWorkspaceId(), meeting.getFolderId()));
    }

    /** REQ-143 영구 삭제 */
    @Transactional
    public void purge(Long meetingId, Long userId) {
        purge(requireTrashedManageable(meetingId, userId));
    }

    /** REQ-143 내가 지웠거나 내가 만든 회의 중 쓰레기통에 있는 것 (지운 순서 최신순). 들어갈 수 없는 폴더의 회의는 뺀다 */
    @Transactional(readOnly = true)
    public List<TrashMeetingResponse> list(Long workspaceId, Long userId) {
        Map<Long, AccessibleFolder> folders = workspaceClient.accessibleFolders(userId).stream()
                .filter(folder -> folder.workspaceId().equals(workspaceId))
                .collect(Collectors.toMap(AccessibleFolder::folderId, Function.identity(), (a, b) -> a));
        List<Meeting> meetings = meetingRepository.findTrash(workspaceId, userId).stream()
                .filter(meeting -> folders.containsKey(meeting.getFolderId()))
                .toList();
        Map<Long, UserSummary> users = meetings.isEmpty() ? Map.of()
                : userClient.findUsers(meetings.stream().map(Meeting::getDeletedByUserId).collect(Collectors.toSet()),
                        workspaceId);
        return meetings.stream()
                .map(meeting -> new TrashMeetingResponse(meeting.getId(), meeting.getTitle(), meeting.getFolderId(),
                        folders.get(meeting.getFolderId()).name(), meeting.getStartAt(),
                        meeting.is(MeetingStatus.FINISHED),
                        PersonResponse.of(meeting.getDeletedByUserId(), users.get(meeting.getDeletedByUserId())),
                        meeting.getDeletedAt(), meeting.getDeletedAt().plus(retention)))
                .toList();
    }

    /** 보관 기간이 지난 회의를 영구 삭제한다. 회의마다 따로 커밋해서 하나가 실패해도 나머지는 지운다 */
    @Scheduled(cron = "${nineouting.meeting.trash-purge-cron:0 50 4 * * *}")
    public void purgeExpired() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (Meeting meeting : meetingRepository.findByDeletedAtBefore(now().minus(retention))) {
            try {
                transaction.executeWithoutResult(status -> purge(meeting));
            } catch (RuntimeException e) {
                log.warn("쓰레기통 회의를 영구 삭제하지 못했습니다. 내일 다시 합니다 (meetingId={}): {}", meeting.getId(), e.toString());
            }
        }
    }

    private void purge(Meeting meeting) {
        purger.purge(List.of(meeting.getId()));
        outboxEventWriter.append(MeetingTrashEvents.TOPIC, meeting.getId().toString(), MeetingPurged.TYPE,
                new MeetingPurged(meeting.getId(), meeting.getWorkspaceId()));
    }

    private Meeting requireTrashedManageable(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .filter(Meeting::isTrashed)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!userId.equals(meeting.getDeletedByUserId()) && !meeting.isOwnedBy(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return meeting;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
