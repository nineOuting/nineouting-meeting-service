package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.AgendaEdit;
import com.nineouting.backend.meeting.domain.AgendaEditKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AgendaEditRepository extends JpaRepository<AgendaEdit, Long> {

    Optional<AgendaEdit> findByMeetingIdAndKindAndItemKey(Long meetingId, AgendaEditKind kind, String itemKey);

    List<AgendaEdit> findByMeetingIdAndSentFalseOrderById(Long meetingId);

    List<AgendaEdit> findByMeetingIdAndReplayTrue(Long meetingId);

    boolean existsByMeetingIdAndSentFalse(Long meetingId);

    @Modifying
    @Query("delete from AgendaEdit e where e.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
