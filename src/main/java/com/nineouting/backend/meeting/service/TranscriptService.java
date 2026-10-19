package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.TranscriptLine;
import com.nineouting.backend.meeting.dto.RoomMessage;
import com.nineouting.backend.meeting.dto.TranscriptLineResponse;
import com.nineouting.backend.meeting.dto.TranscriptSegmentsRequest.Segment;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전사 텍스트 저장·조회·수정 (REQ-072, 112, docs/services/ai-integration.md 2.2).
 * 반영한 줄과 수정·삭제는 커밋된 뒤 회의방 WebSocket으로 참석자 화면에 보낸다 (MeetingRoomRelay).
 */
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private final TranscriptLineRepository lineRepository;
    private final MeetingAccess access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** stt-service가 보낸 줄. 같은 seq는 줄 전체가 오므로 통째로 바꾼다 */
    @Transactional
    public void saveFromStt(Long meetingId, List<Segment> segments) {
        access.requireStarted(meetingId);
        LocalDateTime now = now();
        for (Segment segment : segments) {
            boolean lowConfidence = Boolean.TRUE.equals(segment.lowConfidence());
            boolean recovered = Boolean.TRUE.equals(segment.recovered());
            int applied = lineRepository.upsertFromStt(meetingId, segment.seq(), segment.rev(),
                    segment.speakerLabel(), segment.text(), segment.startMs(), segment.endMs(), segment.finalLine(),
                    lowConfidence, segment.kind(), recovered, now);
            if (applied > 0) {
                events.publishEvent(RoomEvent.send(meetingId, RoomMessage.transcript(new TranscriptLineResponse(
                        segment.seq(), segment.rev(), segment.speakerLabel(), segment.text(), segment.startMs(),
                        segment.endMs(), segment.finalLine(), lowConfidence, segment.kind(), recovered))));
            }
        }
    }

    /** 참석자. 지운 줄은 빼고 startMs 순 */
    @Transactional(readOnly = true)
    public List<TranscriptLineResponse> list(Long meetingId, Long userId) {
        access.requireAttendee(meetingId, userId);
        return lineRepository.findByMeetingIdAndDeletedFalseOrderByStartMsAscSeqAsc(meetingId).stream()
                .map(TranscriptLineResponse::from)
                .toList();
    }

    /** REQ-112. 회의록 작성자, 진행 중. 확정된 줄만 고친다 */
    @Transactional
    public TranscriptLineResponse edit(Long meetingId, Long userId, int seq, String text) {
        TranscriptLine line = requireEditable(meetingId, userId, seq);
        line.editText(text, now());
        TranscriptLineResponse response = TranscriptLineResponse.from(line);
        events.publishEvent(RoomEvent.send(meetingId, RoomMessage.transcriptEdited(response)));
        return response;
    }

    /** REQ-112. 회의록 작성자, 진행 중 */
    @Transactional
    public void delete(Long meetingId, Long userId, int seq) {
        TranscriptLine line = requireEditable(meetingId, userId, seq);
        line.delete(now());
        events.publishEvent(RoomEvent.send(meetingId, RoomMessage.transcriptDeleted(seq, line.getRev())));
    }

    private TranscriptLine requireEditable(Long meetingId, Long userId, int seq) {
        access.requireWriterInProgress(meetingId, userId);
        TranscriptLine line = lineRepository.findForUpdate(meetingId, seq)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!line.isEditable()) {
            throw new BusinessException(MeetingErrorCode.TRANSCRIPT_NOT_EDITABLE);
        }
        return line;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
