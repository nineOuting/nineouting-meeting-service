package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import com.nineouting.backend.meeting.dto.PendingAgendaResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
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

    /** REQ-052. 다음 회의에 불러온(진행 중) 안건은 목록에서 빠진다. 그 회의가 취소되거나 다시 넘기면 대기로 돌아와 다시 보인다 */
    private static final List<PendingAgendaStatus> VISIBLE = List.of(PendingAgendaStatus.WAITING);

    private final PendingAgendaRepository pendingAgendaRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccess access;

    /** 대기 중인 안건, 최근에 넘긴 순 */
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

    /** 대기 중인 안건만 버릴 수 있다. 다른 회의에 불러온 안건은 그 회의에서 빼야 한다 */
    @Transactional
    public void discard(Long folderId, Long userId, Long pendingAgendaId) {
        PendingAgenda pending = requireInFolder(folderId, userId, pendingAgendaId);
        if (!pending.isWaiting()) {
            throw new BusinessException(MeetingErrorCode.PENDING_AGENDA_UNAVAILABLE);
        }
        pending.discard();
    }

    @Transactional
    public void restore(Long folderId, Long userId, Long pendingAgendaId) {
        PendingAgenda pending = requireInFolder(folderId, userId, pendingAgendaId);
        if (!pending.isDiscarded()) {
            throw new BusinessException(MeetingErrorCode.PENDING_AGENDA_NOT_DISCARDED);
        }
        pending.restore();
    }

    private PendingAgenda requireInFolder(Long folderId, Long userId, Long pendingAgendaId) {
        access.requireFolderAccess(folderId, userId);
        return pendingAgendaRepository.findByIdForUpdate(pendingAgendaId)
                .filter(pending -> pending.getFolderId().equals(folderId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
