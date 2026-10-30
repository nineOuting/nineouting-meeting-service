package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.AiBatch;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiBatchRepository extends JpaRepository<AiBatch, Long> {

    List<AiBatch> findByMeetingIdAndBatchGreaterThanEqualOrderByBatch(Long meetingId, int fromBatch);

    @Modifying
    @Query("delete from AiBatch b where b.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
