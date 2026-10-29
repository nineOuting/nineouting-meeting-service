package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.AgendaState;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AgendaStateRepository extends JpaRepository<AgendaState, Long> {

    /** 보드·분류가 동시에 와도 순서대로 반영하도록 잠그고 읽는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AgendaState s where s.meetingId = :meetingId")
    Optional<AgendaState> findForUpdate(Long meetingId);

    /** 새 안건 제안이 뜬 지 오래된 회의 (30초 자동 무시, REQ-091) */
    @Query("select s.meetingId from AgendaState s where s.offTopicSuggestedAt <= :before")
    List<Long> findOffTopicSuggestedBefore(LocalDateTime before);

    @Modifying
    @Query("delete from AgendaState s where s.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
