package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.SttTicket;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SttTicketRepository extends JpaRepository<SttTicket, String> {

    /** 지운 행 수. 같은 입장권을 동시에 써도 한쪽만 1을 받는다 */
    @Modifying
    @Query("delete from SttTicket t where t.token = :token")
    int deleteByToken(String token);

    @Modifying
    @Query("delete from SttTicket t where t.expiresAt <= :now")
    int deleteExpired(LocalDateTime now);

    @Modifying
    @Query("delete from SttTicket t where t.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
