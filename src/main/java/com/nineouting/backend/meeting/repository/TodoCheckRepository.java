package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.TodoCheck;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TodoCheckRepository extends JpaRepository<TodoCheck, TodoCheck.Key> {

    List<TodoCheck> findByMeetingIdOrderByCheckedAt(Long meetingId);

    List<TodoCheck> findByMeetingIdIn(Collection<Long> meetingIds);

    /** 이미 체크했으면 그대로 둔다 (두 사람이 함께 눌러도 한 줄) */
    @Modifying
    @Query(value = """
            insert into todo_checks (meeting_id, item_id, checked_by, checked_at)
            values (:meetingId, :itemId, :userId, :now)
            on conflict (meeting_id, item_id) do nothing""", nativeQuery = true)
    void insertIgnoringDuplicate(Long meetingId, String itemId, Long userId, LocalDateTime now);

    @Modifying
    @Query("delete from TodoCheck c where c.meetingId = :meetingId and c.itemId = :itemId")
    void deleteOne(Long meetingId, String itemId);

    @Modifying
    @Query("delete from TodoCheck c where c.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
