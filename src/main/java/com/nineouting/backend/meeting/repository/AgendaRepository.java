package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.Agenda;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AgendaRepository extends JpaRepository<Agenda, Long> {

    List<Agenda> findByMeetingIdOrderByPosition(Long meetingId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Agenda a where a.meetingId = :meetingId")
    void deleteByMeetingId(Long meetingId);

    @Modifying
    @Query("delete from Agenda a where a.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
