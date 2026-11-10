package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    /** 쓰레기통에 있는 회의는 뺀다 (REQ-166) */
    Page<Meeting> findByFolderIdAndStatusNotAndDeletedAtIsNull(Long folderId, MeetingStatus status, Pageable pageable);

    /** 홈 캘린더 (REQ-018): from 이상 to 미만에 시작하는 회의 */
    @Query("""
            select m from Meeting m
            where m.folderId in :folderIds and m.status <> :excluded and m.deletedAt is null
              and m.startAt >= :from and m.startAt < :to
            order by m.startAt, m.id""")
    List<Meeting> findCalendar(Collection<Long> folderIds, MeetingStatus excluded, LocalDateTime from, LocalDateTime to);

    /** REQ-143 내가 지웠거나 내가 만든 회의 중 쓰레기통에 있는 것 (지운 순서 최신순) */
    @Query("""
            select m from Meeting m
            where m.workspaceId = :workspaceId and m.deletedAt is not null
              and (m.deletedByUserId = :userId or m.ownerUserId = :userId)
            order by m.deletedAt desc, m.id desc""")
    List<Meeting> findTrash(Long workspaceId, Long userId);

    /** 보관 기간이 지난 쓰레기통 회의 */
    List<Meeting> findByDeletedAtBefore(LocalDateTime before);

    @Query("select m.id from Meeting m where m.folderId in :folderIds")
    List<Long> findIdsByFolderIdIn(Collection<Long> folderIds);

    @Modifying
    @Query("delete from Meeting m where m.id in :ids")
    void deleteAllByIdIn(Collection<Long> ids);
}
