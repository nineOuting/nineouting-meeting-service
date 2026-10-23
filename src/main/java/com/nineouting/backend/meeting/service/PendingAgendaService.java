package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import com.nineouting.backend.meeting.dto.PendingAgendaResponse;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.PendingAgendaRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폴더의 남은 안건 (REQ-051~052).
 */
@Service
@RequiredArgsConstructor
public class PendingAgendaService {

    private static final List<PendingAgendaStatus> VISIBLE =
            List.of(PendingAgendaStatus.WAITING, PendingAgendaStatus.IN_PROGRESS);

    private final PendingAgendaRepository pendingAgendaRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccess access;

    /** 대기·진행 중인 안건, 최근에 넘긴 순 */
    @Transactional(readOnly = true)
    public List<PendingAgendaResponse> list(Long folderId, Long userId) {
        access.requireFolderAccess(folderId, userId);
        List<PendingAgenda> pendingAgendas =
                pendingAgendaRepository.findByFolderIdAndStatusInOrderByCarriedAtDescIdDesc(folderId, VISIBLE);
        Map<Long, String> meetingTitles = meetingRepository.findAllById(pendingAgendas.stream()
                        .map(PendingAgenda::getFromMeetingId).distinct().toList()).stream()
                .collect(Collectors.toMap(Meeting::getId, Meeting::getTitle));
        return pendingAgendas.stream()
                .map(pending -> new PendingAgendaResponse(pending.getId(), pending.getTitle(), List.copyOf(pending.getDecisionPoints()),
                        pending.getStatus(), pending.getFromMeetingId(), meetingTitles.get(pending.getFromMeetingId()),
                        pending.getCarriedAt(), pending.getTakenMeetingId()))
                .toList();
    }
}
