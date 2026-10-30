package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.dto.LiveSummaryItem;
import com.nineouting.backend.meeting.dto.LiveSummaryRequest;
import com.nineouting.backend.meeting.dto.LiveSummaryResponse;
import com.nineouting.backend.meeting.dto.RoomMessage;
import com.nineouting.backend.meeting.repository.LiveSummaryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의 중 요약 (REQ-085, docs/services/ai-integration.md 2.5). ai-service가 1분 정도마다 바뀐 요약을 통째로 보낸다.
 * 회의마다 가장 최신 하나만 두고, 반영하면 커밋 뒤 회의방으로 보낸다.
 */
@Service
@RequiredArgsConstructor
public class LiveSummaryService {

    private static final TypeReference<List<LiveSummaryItem>> ITEMS = new TypeReference<>() { };

    private final LiveSummaryRepository summaryRepository;
    private final MeetingAccess access;
    private final ApplicationEventPublisher events;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * ai-service가 보낸 요약. 시작한 회의(진행 중·끝남)만 받는다 (마지막 요약은 회의가 끝난 뒤에 온다).
     * 저장된 것보다 version이 작거나 같으면 조용히 버린다.
     */
    @Transactional
    public void receive(Long meetingId, LiveSummaryRequest request) {
        if (request.meetingId() != null && !request.meetingId().equals(meetingId.toString())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "주소와 본문의 meetingId가 다릅니다");
        }
        access.requireStarted(meetingId);
        LocalDateTime now = LocalDateTime.now(clock);
        int applied = summaryRepository.upsertIfNewer(meetingId, request.version(), request.atMs(),
                request.finalLine(), jsonMapper.writeValueAsString(request.summary()), now);
        if (applied > 0) {
            events.publishEvent(RoomEvent.send(meetingId, RoomMessage.liveSummary(new LiveSummaryResponse(
                    request.version(), request.atMs(), request.finalLine(), request.summary(), now))));
        }
    }

    /** 참석자. 아직 요약이 없으면 null */
    @Transactional(readOnly = true)
    public LiveSummaryResponse find(Long meetingId, Long userId) {
        access.requireAttendee(meetingId, userId);
        return summaryRepository.findById(meetingId)
                .map(summary -> new LiveSummaryResponse(summary.getVersion(), summary.getAtMs(),
                        summary.isFinalSummary(), jsonMapper.readValue(summary.getSummary(), ITEMS),
                        summary.getReceivedAt()))
                .orElse(null);
    }
}
