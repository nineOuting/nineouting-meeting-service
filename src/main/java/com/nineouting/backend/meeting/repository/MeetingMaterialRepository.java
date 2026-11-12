package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.MeetingMaterial;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MeetingMaterialRepository extends JpaRepository<MeetingMaterial, Long> {

    List<MeetingMaterial> findByMeetingIdAndStatusOrderById(Long meetingId, MeetingMaterial.Status status);

    long countByMeetingIdAndStatus(Long meetingId, MeetingMaterial.Status status);

    List<MeetingMaterial> findByMeetingIdIn(Collection<Long> meetingIds);

    /** 검색용 저장소에 보낼 차례인 자료 하나. 여러 파드가 같은 자료를 보내지 않게 잠근다 */
    @Query(value = """
            select * from meeting_materials
            where rag_status = 'PENDING' and rag_next_attempt_at <= :now and status = 'READY'
            order by rag_next_attempt_at, id
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<MeetingMaterial> lockNextRagDue(LocalDateTime now);

    /** 접수됐는데 콜백이 오지 않은 자료 하나 (상태 조회로 확인한다) */
    @Query(value = """
            select * from meeting_materials
            where rag_status = 'ACCEPTED' and rag_sent_at <= :sentBefore
            order by rag_sent_at, id
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<MeetingMaterial> lockNextRagStale(LocalDateTime sentBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MeetingMaterial m where m.id = :id")
    Optional<MeetingMaterial> findForUpdate(Long id);

    @Modifying
    @Query("delete from MeetingMaterial m where m.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
