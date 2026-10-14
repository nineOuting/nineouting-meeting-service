package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.MeetingAttendee;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, Long> {

    List<MeetingAttendee> findByMeetingIdOrderById(Long meetingId);

    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    @Modifying
    @Query("delete from MeetingAttendee a where a.meetingId = :meetingId and a.userId in :userIds")
    void deleteByMeetingIdAndUserIdIn(Long meetingId, Collection<Long> userIds);

    @Modifying
    @Query("delete from MeetingAttendee a where a.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
