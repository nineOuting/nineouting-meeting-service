package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.TranscriptLine;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TranscriptLineRepository extends JpaRepository<TranscriptLine, Long> {

    /**
     * stt-service가 보낸 줄을 넣거나 바꾼다 (docs/services/ai-integration.md 2.2).
     * rev가 더 클 때만 바꾸고, 확정되거나 지운 줄은 그대로 둔다 (확정 뒤 변경은 모두 작성자 수정).
     * 한 문장이라 같은 줄이 동시에 와도 안전하다.
     */
    @Modifying
    @Query(value = """
            insert into transcript_lines (meeting_id, seq, rev, speaker_label, text, start_ms, end_ms, is_final,
                                          low_confidence, kind, recovered, deleted, updated_at)
            values (:meetingId, :seq, :rev, :speakerLabel, :text, :startMs, :endMs, :isFinal,
                    :lowConfidence, :kind, :recovered, false, :now)
            on conflict (meeting_id, seq) do update set
                rev = excluded.rev, speaker_label = excluded.speaker_label, text = excluded.text,
                start_ms = excluded.start_ms, end_ms = excluded.end_ms, is_final = excluded.is_final,
                low_confidence = excluded.low_confidence, kind = excluded.kind, recovered = excluded.recovered,
                updated_at = excluded.updated_at
            where transcript_lines.rev < excluded.rev
              and not transcript_lines.is_final
              and not transcript_lines.deleted""", nativeQuery = true)
    void upsertFromStt(Long meetingId, int seq, int rev, String speakerLabel, String text, long startMs, Long endMs,
                       boolean isFinal, boolean lowConfidence, String kind, boolean recovered, LocalDateTime now);

    /** 화면·저장 순서는 startMs 기준 (보완 줄이 앞 시각에 끼워진다) */
    List<TranscriptLine> findByMeetingIdAndDeletedFalseOrderByStartMsAscSeqAsc(Long meetingId);

    /** 작성자 수정이 STT 갱신과 엇갈리지 않게 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TranscriptLine l where l.meetingId = :meetingId and l.seq = :seq")
    Optional<TranscriptLine> findForUpdate(Long meetingId, int seq);

    /** ai-service로 넘길 후보: 아직 안 넘긴 확정 줄, 넘긴 뒤 바뀐 줄 */
    @Query("""
            select l from TranscriptLine l
            where l.meetingId = :meetingId
              and ((l.finalLine = true and l.sentRev is null and l.deleted = false)
                   or (l.sentRev is not null and l.rev > l.sentRev))
            order by l.startMs, l.seq""")
    List<TranscriptLine> findPendingForAi(Long meetingId);

    /**
     * ai-service에 넘긴 rev를 기록한다. 줄 전체를 저장하지 않고 이 칸만 바꿔서,
     * 그사이 작성자가 고치거나 지운 내용을 덮어쓰지 않는다 (고친 줄은 rev가 커서 다음 묶음에 다시 간다).
     */
    @Modifying
    @Query("update TranscriptLine l set l.sentRev = :rev where l.id = :id")
    void markSentToAi(Long id, int rev);

    @Modifying
    @Query("delete from TranscriptLine l where l.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
