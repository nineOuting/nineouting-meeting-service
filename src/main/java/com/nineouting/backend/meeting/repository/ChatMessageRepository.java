package com.nineouting.backend.meeting.repository;

import com.nineouting.backend.meeting.domain.ChatMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByMeetingIdAndUserIdOrderById(Long meetingId, Long userId);

    /** 최근 대화 (새것부터). ai-service에 넘길 때 뒤집어 오래된 것부터 담는다 */
    List<ChatMessage> findByMeetingIdAndUserIdOrderByIdDesc(Long meetingId, Long userId, Pageable pageable);

    @Modifying
    @Query("delete from ChatMessage m where m.meetingId in :meetingIds")
    void deleteByMeetingIdIn(Collection<Long> meetingIds);
}
