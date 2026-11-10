package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.AiFeedback;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {

    Optional<AiFeedback> findByMeetingIdAndUserIdAndTarget(Long meetingId, Long userId, AiFeedback.Target target);

    @Modifying
    @Query("delete from AiFeedback f where f.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
