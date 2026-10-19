package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.AiFeed;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiFeedRepository extends JpaRepository<AiFeed, Long> {

    /**
     * 보낼 차례인 회의 하나를 잠그고 가져온다. 다른 인스턴스가 잠근 회의는 건너뛴다.
     * 줄 묶음 차례가 됐거나, 회의 알림이 쌓였고 ai-service가 정상이면(실패 중이 아니면) 차례다.
     */
    @Query(value = """
            select * from ai_feeds f
            where f.next_attempt_at <= :now
               or (f.failures = 0 and exists (select 1 from progress_events e
                                              where e.meeting_id = f.meeting_id and not e.sent))
            order by f.next_attempt_at
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<AiFeed> lockNextDue(LocalDateTime now);

    @Modifying
    @Query("delete from AiFeed f where f.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
