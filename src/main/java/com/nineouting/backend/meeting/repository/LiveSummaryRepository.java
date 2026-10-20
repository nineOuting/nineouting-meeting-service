package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.LiveSummary;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LiveSummaryRepository extends JpaRepository<LiveSummary, Long> {

    /**
     * 더 큰 version만 반영한다 (순서가 뒤섞여 와도 최신이 남는다). 한 문장이라 동시에 와도 안전하다.
     *
     * @return 반영했으면 1, 무시했으면 0
     */
    @Modifying
    @Query(value = """
            insert into live_summaries (meeting_id, version, at_ms, is_final, summary, received_at)
            values (:meetingId, :version, :atMs, :isFinal, :summary, :now)
            on conflict (meeting_id) do update set
                version = excluded.version, at_ms = excluded.at_ms, is_final = excluded.is_final,
                summary = excluded.summary, received_at = excluded.received_at
            where live_summaries.version < excluded.version""", nativeQuery = true)
    int upsertIfNewer(Long meetingId, long version, long atMs, boolean isFinal, String summary, LocalDateTime now);

    @Modifying
    @Query("delete from LiveSummary s where s.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
