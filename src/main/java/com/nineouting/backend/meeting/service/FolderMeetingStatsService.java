package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.AgendaStatus;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.dto.FolderMeetingStatsResponse;
import com.nineouting.backend.meeting.dto.FolderMeetingStatsResponse.MeetingStat;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.PendingAgendaRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-200 프로젝트 화면의 안건 결정률, 예정 시간 초과. 최근 끝난 회의 6개를 센다.
 * 안건 결과: 결정(DECIDED) / 넘김(다음 회의로 넘긴 남은 안건) / 제외(나머지)
 */
@Service
@RequiredArgsConstructor
public class FolderMeetingStatsService {

    private final MeetingRepository meetingRepository;
    private final AgendaRepository agendaRepository;
    private final PendingAgendaRepository pendingAgendaRepository;
    private final MeetingAccess access;

    @Transactional(readOnly = true)
    public FolderMeetingStatsResponse stats(Long folderId, Long userId) {
        access.requireFolderAccess(folderId, userId);
        List<Meeting> meetings = new ArrayList<>(
                meetingRepository.findTop6ByFolderIdAndStatusAndDeletedAtIsNullOrderByStartAtDesc(folderId, MeetingStatus.FINISHED));
        if (meetings.isEmpty()) {
            return new FolderMeetingStatsResponse(List.of());
        }
        Collections.reverse(meetings);
        List<Long> ids = meetings.stream().map(Meeting::getId).toList();
        Map<Long, List<Agenda>> agendas = agendaRepository.findByMeetingIdIn(ids).stream()
                .collect(Collectors.groupingBy(Agenda::getMeetingId));
        Map<Long, Set<String>> carried = pendingAgendaRepository.findByFromMeetingIdIn(ids).stream()
                .collect(Collectors.groupingBy(PendingAgenda::getFromMeetingId,
                        Collectors.mapping(PendingAgenda::getTitle, Collectors.toSet())));

        return new FolderMeetingStatsResponse(meetings.stream().map(meeting -> {
            List<Agenda> list = agendas.getOrDefault(meeting.getId(), List.of());
            int decided = (int) list.stream().filter(agenda -> agenda.getStatus() == AgendaStatus.DECIDED).count();
            int carriedCount = Math.min(carried.getOrDefault(meeting.getId(), Set.of()).size(), list.size() - decided);
            return new MeetingStat(meeting.getId(), meeting.getTitle(), meeting.getStartAt().toLocalDate(), decided,
                    carriedCount, list.size() - decided - carriedCount,
                    Duration.between(meeting.getStartAt(), meeting.getEndAt()).toMinutes(), actualMinutes(meeting));
        }).toList());
    }

    private static long actualMinutes(Meeting meeting) {
        if (meeting.getStartedAt() == null || meeting.getFinishedAt() == null) {
            return 0;
        }
        long ms = Duration.between(meeting.getStartedAt(), meeting.getFinishedAt()).toMillis() - meeting.getPausedTotalMs();
        return Math.max(0, Duration.ofMillis(ms).toMinutes());
    }
}
