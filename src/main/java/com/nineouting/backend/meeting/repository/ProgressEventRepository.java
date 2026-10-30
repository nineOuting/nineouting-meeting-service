package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.ProgressEvent;
import com.nineouting.backend.meeting.domain.ProgressEventType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProgressEventRepository extends JpaRepository<ProgressEvent, Long> {

    /** 같은 (회의, 알림, atMs)가 다시 오면 무시한다 */
    @Modifying
    @Query(value = """
            insert into progress_events (meeting_id, event, at_ms, received_at, sent)
            values (:meetingId, :event, :atMs, :receivedAt, false)
            on conflict (meeting_id, event, at_ms) do nothing""", nativeQuery = true)
    void insertIgnoringDuplicate(Long meetingId, String event, long atMs, LocalDateTime receivedAt);

    List<ProgressEvent> findByMeetingIdAndSentFalseOrderById(Long meetingId);

    boolean existsByMeetingIdAndSentFalse(Long meetingId);

    boolean existsByMeetingIdAndEvent(Long meetingId, ProgressEventType event);

    @Modifying
    @Query("delete from ProgressEvent e where e.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
