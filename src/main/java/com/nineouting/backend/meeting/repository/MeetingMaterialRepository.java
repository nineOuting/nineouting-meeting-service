package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.MeetingMaterial;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MeetingMaterialRepository extends JpaRepository<MeetingMaterial, Long> {

    List<MeetingMaterial> findByMeetingIdAndStatusOrderById(Long meetingId, MeetingMaterial.Status status);

    long countByMeetingIdAndStatus(Long meetingId, MeetingMaterial.Status status);

    List<MeetingMaterial> findByMeetingIdIn(Collection<Long> meetingIds);

    @Modifying
    @Query("delete from MeetingMaterial m where m.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
