package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.FolderAccess;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.event.meeting.MeetingCancelled;
import com.nineouting.backend.common.event.meeting.MeetingCreated;
import com.nineouting.backend.common.event.meeting.MeetingMoved;
import com.nineouting.backend.common.event.meeting.MeetingUpdated;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingAttendee;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import com.nineouting.backend.meeting.dto.AgendaRequest;
import com.nineouting.backend.meeting.dto.AgendaResponse;
import com.nineouting.backend.meeting.dto.CalendarMeetingResponse;
import com.nineouting.backend.meeting.dto.MeetingDetailResponse;
import com.nineouting.backend.meeting.dto.MeetingPageResponse;
import com.nineouting.backend.meeting.dto.MeetingRequest;
import com.nineouting.backend.meeting.dto.MeetingSummaryResponse;
import com.nineouting.backend.meeting.dto.PersonResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.PendingAgendaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의 생성·조회·수정·취소·폴더 이동, 폴더 내 회의 목록, 홈 캘린더.
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    /** 월 화면(6주)을 한 번에 받을 수 있는 기간 */
    static final int CALENDAR_MAX_DAYS = 42;
    private static final int MAX_PAGE_SIZE = 100;

    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final AgendaRepository agendaRepository;
    private final PendingAgendaRepository pendingAgendaRepository;
    private final MeetingAccess access;
    private final WorkspaceClient workspaceClient;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    /** REQ-047, 056~062, 064~067. 만든 사람은 참석자로 자동으로 들어간다. */
    @Transactional
    public MeetingDetailResponse create(Long folderId, Long userId, MeetingRequest request) {
        FolderAccess folder = access.requireFolderAccess(folderId, userId);
        Set<Long> attendees = attendeesOf(request, userId);
        validate(folderId, request, attendees);
        if (request.previousMeetingId() != null) {
            meetingRepository.findById(request.previousMeetingId())
                    .filter(previous -> previous.getFolderId().equals(folderId))
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        }

        Meeting meeting = new Meeting(folder.workspaceId(), folderId, userId, request.previousMeetingId());
        meeting.changeInfo(request.title(), request.startAt(), request.endAt(), request.location(), request.writerUserId());
        meetingRepository.save(meeting);
        attendees.forEach(attendee -> attendeeRepository.save(new MeetingAttendee(meeting.getId(), attendee)));
        saveAgendas(meeting, request.agendas());

        outboxEventWriter.append(MeetingCreated.TOPIC, meeting.getId().toString(), MeetingCreated.TYPE,
                new MeetingCreated(meeting.getId(), meeting.getWorkspaceId(), folderId, meeting.getTitle(),
                        meeting.getStartAt(), userId, List.copyOf(attendees)));
        return detail(meeting);
    }

    /** REQ-068, 069 (대기 화면), REQ-060 (이전 설정 불러오기) */
    @Transactional(readOnly = true)
    public MeetingDetailResponse get(Long meetingId, Long userId) {
        return detail(access.requireViewable(meetingId, userId));
    }

    /** REQ-046. 시작 시각 최신순, 취소된 회의는 숨긴다. */
    @Transactional(readOnly = true)
    public MeetingPageResponse list(Long folderId, Long userId, int page, int size) {
        access.requireFolderAccess(folderId, userId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id")));
        Page<Meeting> result = meetingRepository.findByFolderIdAndStatusNot(folderId, MeetingStatus.CANCELLED, pageable);
        return new MeetingPageResponse(result.map(MeetingSummaryResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.hasNext());
    }

    /** REQ-115. 만든 사람만, 시작 전에만. 새로 추가된 사람·빠진 사람을 이벤트에 담는다. */
    @Transactional
    public MeetingDetailResponse update(Long meetingId, Long userId, MeetingRequest request) {
        Meeting meeting = access.requireEditable(meetingId, userId);
        Set<Long> attendees = attendeesOf(request, meeting.getOwnerUserId());
        validate(meeting.getFolderId(), request, attendees);

        Set<Long> before = attendeeIds(meetingId);
        Set<Long> added = difference(attendees, before);
        Set<Long> removed = difference(before, attendees);
        if (!removed.isEmpty()) {
            attendeeRepository.deleteByMeetingIdAndUserIdIn(meetingId, removed);
        }
        added.forEach(attendee -> attendeeRepository.save(new MeetingAttendee(meetingId, attendee)));

        Set<Long> keptPendingIds = request.agendas().stream()
                .map(AgendaRequest::pendingAgendaId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        agendaRepository.findByMeetingIdOrderByPosition(meetingId).stream()
                .map(Agenda::getPendingAgendaId)
                .filter(id -> id != null && !keptPendingIds.contains(id))
                .forEach(id -> pendingAgendaRepository.findByIdForUpdate(id)
                        .filter(pending -> pending.isTakenBy(meetingId))
                        .ifPresent(PendingAgenda::release));
        agendaRepository.deleteByMeetingId(meetingId);
        saveAgendas(meeting, request.agendas());
        meeting.changeInfo(request.title(), request.startAt(), request.endAt(), request.location(), request.writerUserId());

        outboxEventWriter.append(MeetingUpdated.TOPIC, meetingId.toString(), MeetingUpdated.TYPE,
                new MeetingUpdated(meetingId, meeting.getWorkspaceId(), meeting.getFolderId(), meeting.getTitle(),
                        meeting.getStartAt(), meeting.getOwnerUserId(), List.copyOf(attendees), List.copyOf(added),
                        List.copyOf(removed)));
        return detail(meeting);
    }

    /** REQ-112. 만든 사람만, 시작 전에만. 불러온 남은 안건은 다시 대기로 돌린다. */
    @Transactional
    public void cancel(Long meetingId, Long userId) {
        Meeting meeting = access.requireEditable(meetingId, userId);
        meeting.cancel(LocalDateTime.now(clock));
        pendingAgendaRepository.findByTakenMeetingIdAndStatus(meetingId, PendingAgendaStatus.IN_PROGRESS)
                .forEach(PendingAgenda::release);

        outboxEventWriter.append(MeetingCancelled.TOPIC, meetingId.toString(), MeetingCancelled.TYPE,
                new MeetingCancelled(meetingId, meeting.getWorkspaceId(), meeting.getFolderId(), meeting.getTitle(),
                        meeting.getStartAt(), meeting.getOwnerUserId(), List.copyOf(attendeeIds(meetingId))));
    }

    /** REQ-043. 만든 사람만, 상태와 상관없이. 같은 워크스페이스에서 들어갈 수 있는 폴더로만 옮긴다. */
    @Transactional
    public void move(Long meetingId, Long userId, Long folderId) {
        Meeting meeting = access.requireOwner(meetingId, userId);
        if (meeting.getFolderId().equals(folderId)) {
            return;
        }
        FolderAccess target = workspaceClient.access(folderId, userId);
        if (!target.workspaceId().equals(meeting.getWorkspaceId())) {
            throw new BusinessException(MeetingErrorCode.FOLDER_MOVE_INVALID);
        }
        if (!target.allowed()) {
            throw new BusinessException(MeetingErrorCode.FOLDER_ACCESS_DENIED);
        }
        Long fromFolderId = meeting.getFolderId();
        meeting.moveTo(folderId);
        outboxEventWriter.append(MeetingMoved.TOPIC, meetingId.toString(), MeetingMoved.TYPE,
                new MeetingMoved(meetingId, meeting.getWorkspaceId(), fromFolderId, folderId));
    }

    /** REQ-018~020. 내가 들어갈 수 있는 모든 워크스페이스·폴더의 회의 (from~to, 양 끝 포함) */
    @Transactional(readOnly = true)
    public List<CalendarMeetingResponse> calendar(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > CALENDAR_MAX_DAYS) {
            throw new BusinessException(MeetingErrorCode.CALENDAR_RANGE_INVALID);
        }
        Map<Long, AccessibleFolder> folders = workspaceClient.accessibleFolders(userId).stream()
                .collect(Collectors.toMap(AccessibleFolder::folderId, Function.identity(), (a, b) -> a));
        if (folders.isEmpty()) {
            return List.of();
        }
        return meetingRepository.findCalendar(folders.keySet(), MeetingStatus.CANCELLED, from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay()).stream()
                .map(meeting -> {
                    AccessibleFolder folder = folders.get(meeting.getFolderId());
                    return new CalendarMeetingResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(),
                            meeting.getEndAt(), meeting.getStatus(), meeting.getMinutesStatus(), meeting.getWorkspaceId(),
                            folder.workspaceName(),
                            meeting.getFolderId(), folder.name());
                })
                .toList();
    }

    MeetingDetailResponse detail(Meeting meeting) {
        Set<Long> attendees = attendeeIds(meeting.getId());
        Set<Long> userIds = new LinkedHashSet<>(attendees);
        userIds.add(meeting.getOwnerUserId());
        userIds.add(meeting.getWriterUserId());
        Map<Long, UserSummary> users = userClient.findUsers(userIds);
        return new MeetingDetailResponse(
                meeting.getId(), meeting.getWorkspaceId(), meeting.getFolderId(), meeting.getTitle(),
                meeting.getStartAt(), meeting.getEndAt(), meeting.getLocation(), meeting.getStatus(),
                meeting.getMinutesStatus(),
                PersonResponse.of(meeting.getOwnerUserId(), users.get(meeting.getOwnerUserId())),
                PersonResponse.of(meeting.getWriterUserId(), users.get(meeting.getWriterUserId())),
                attendees.stream().map(id -> PersonResponse.of(id, users.get(id))).toList(),
                agendaRepository.findByMeetingIdOrderByPosition(meeting.getId()).stream().map(AgendaResponse::from).toList(),
                meeting.getPreviousMeetingId(), meeting.getStartedAt(), meeting.getFinishedAt());
    }

    /** 요청한 참석자 + 만든 사람 (요청 순서 유지) */
    private static Set<Long> attendeesOf(MeetingRequest request, Long ownerUserId) {
        Set<Long> attendees = new LinkedHashSet<>();
        attendees.add(ownerUserId);
        attendees.addAll(request.attendeeUserIds());
        return attendees;
    }

    /** REQ-057 시간, REQ-058 참석자는 폴더에 들어갈 수 있는 사람만, 회의록 작성자는 참석자 중에서 */
    private void validate(Long folderId, MeetingRequest request, Set<Long> attendees) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessException(MeetingErrorCode.MEETING_TIME_INVALID);
        }
        if (!attendees.contains(request.writerUserId())) {
            throw new BusinessException(MeetingErrorCode.WRITER_NOT_ATTENDEE);
        }
        if (!workspaceClient.folderMembers(folderId).containsAll(attendees)) {
            throw new BusinessException(MeetingErrorCode.ATTENDEE_NOT_FOLDER_MEMBER);
        }
    }

    /** 안건은 요청 순서대로 저장한다. 남은 안건을 불러오면 진행 중으로 바꾼다 (REQ-050, 064). */
    private void saveAgendas(Meeting meeting, List<AgendaRequest> agendas) {
        Set<Long> usedPendingIds = new HashSet<>();
        for (int position = 0; position < agendas.size(); position++) {
            AgendaRequest agenda = agendas.get(position);
            Long pendingId = agenda.pendingAgendaId();
            if (pendingId != null) {
                PendingAgenda pending = pendingAgendaRepository.findByIdForUpdate(pendingId)
                        .filter(found -> found.getFolderId().equals(meeting.getFolderId()))
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
                boolean available = pending.isWaiting() || pending.isTakenBy(meeting.getId());
                if (!available || !usedPendingIds.add(pendingId)) {
                    throw new BusinessException(MeetingErrorCode.PENDING_AGENDA_UNAVAILABLE);
                }
                pending.takeBy(meeting.getId());
            }
            agendaRepository.save(new Agenda(meeting.getId(), position, agenda.title(), agenda.decisionPoint(),
                    pendingId, false));
        }
    }

    private Set<Long> attendeeIds(Long meetingId) {
        return attendeeRepository.findByMeetingIdOrderById(meetingId).stream()
                .map(MeetingAttendee::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<Long> difference(Set<Long> left, Set<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
