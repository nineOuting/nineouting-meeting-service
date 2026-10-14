package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.PendingAgenda;
import com.nineouting.backend.meeting.domain.PendingAgendaStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PendingAgendaRepository extends JpaRepository<PendingAgenda, Long> {

    /** 두 회의가 같은 남은 안건을 동시에 불러가지 않게 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingAgenda p where p.id = :id")
    Optional<PendingAgenda> findByIdForUpdate(Long id);

    List<PendingAgenda> findByFolderIdAndStatusInOrderByCarriedAtDescIdDesc(Long folderId,
                                                                           Collection<PendingAgendaStatus> statuses);

    List<PendingAgenda> findByTakenMeetingIdAndStatus(Long takenMeetingId, PendingAgendaStatus status);

    @Modifying
    @Query("delete from PendingAgenda p where p.folderId in :folderIds")
    void deleteByFolderIdIn(Collection<Long> folderIds);
}
