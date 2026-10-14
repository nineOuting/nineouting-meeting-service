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

    Page<Meeting> findByFolderIdAndStatusNot(Long folderId, MeetingStatus status, Pageable pageable);

    /** 홈 캘린더 (REQ-018): from 이상 to 미만에 시작하는 회의 */
    @Query("""
            select m from Meeting m
            where m.folderId in :folderIds and m.status <> :excluded and m.startAt >= :from and m.startAt < :to
            order by m.startAt, m.id""")
    List<Meeting> findCalendar(Collection<Long> folderIds, MeetingStatus excluded, LocalDateTime from, LocalDateTime to);

    @Query("select m.id from Meeting m where m.folderId in :folderIds")
    List<Long> findIdsByFolderIdIn(Collection<Long> folderIds);

    @Modifying
    @Query("delete from Meeting m where m.id in :ids")
    void deleteAllByIdIn(Collection<Long> ids);
}
