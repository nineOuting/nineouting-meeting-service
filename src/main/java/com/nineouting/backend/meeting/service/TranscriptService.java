package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.TranscriptLine;
import com.nineouting.backend.meeting.dto.TranscriptLineResponse;
import com.nineouting.backend.meeting.dto.TranscriptSegmentsRequest.Segment;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전사 텍스트 저장·조회·수정 (REQ-072, 112, docs/services/ai-integration.md 2.2).
 * 참석자 화면으로 실시간 중계(회의방 WebSocket)는 아직 없다 (meeting 2차).
 */
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private final TranscriptLineRepository lineRepository;
    private final MeetingAccess access;
    private final Clock clock;

    /** stt-service가 보낸 줄. 같은 seq는 줄 전체가 오므로 통째로 바꾼다 */
    @Transactional
    public void saveFromStt(Long meetingId, List<Segment> segments) {
        access.requireStarted(meetingId);
        LocalDateTime now = now();
        for (Segment segment : segments) {
            lineRepository.upsertFromStt(meetingId, segment.seq(), segment.rev(), segment.speakerLabel(),
                    segment.text(), segment.startMs(), segment.endMs(), segment.finalLine(),
                    Boolean.TRUE.equals(segment.lowConfidence()), segment.kind(),
                    Boolean.TRUE.equals(segment.recovered()), now);
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
        return TranscriptLineResponse.from(line);
    }

    /** REQ-112. 회의록 작성자, 진행 중 */
    @Transactional
    public void delete(Long meetingId, Long userId, int seq) {
        requireEditable(meetingId, userId, seq).delete(now());
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
