package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.LineAgenda;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LineAgendaRepository extends JpaRepository<LineAgenda, LineAgenda.Key> {

    List<LineAgenda> findByMeetingIdOrderBySeq(Long meetingId);

    List<LineAgenda> findByMeetingIdAndSeqIn(Long meetingId, Collection<Integer> seqs);

    @Modifying
    @Query("delete from LineAgenda l where l.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
