package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.SttTicket;
import com.nineouting.backend.meeting.dto.SttTicketOwnerResponse;
import com.nineouting.backend.meeting.dto.SttTicketResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.SttTicketRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 음성 연결 1회용 입장권 (REQ-072, docs/services/ai-integration.md 2.1).
 * 회의록 작성자의 브라우저만 음성을 연결할 수 있다. stt-service는 JWT를 몰라도 입장권으로 확인한다.
 */
@Service
@RequiredArgsConstructor
public class SttTicketService {

    static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final int TOKEN_BYTES = 32;

    private final SttTicketRepository ticketRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccess access;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 회의록 작성자, 진행 중 */
    @Transactional
    public SttTicketResponse issue(Long meetingId, Long userId) {
        access.requireWriterInProgress(meetingId, userId);
        String token = newToken();
        ticketRepository.save(new SttTicket(token, meetingId, userId, now().plus(TICKET_TTL)));
        return new SttTicketResponse(token, TICKET_TTL.toSeconds());
    }

    /**
     * stt-service가 연결을 받을 때 한 번 확인한다. 확인한 입장권은 지운다.
     * 그사이 회의가 끝났거나 작성자가 바뀌었어도 거절한다.
     */
    @Transactional
    public SttTicketOwnerResponse consume(String token) {
        SttTicket ticket = ticketRepository.findById(token).orElseThrow(SttTicketService::invalid);
        if (ticketRepository.deleteByToken(token) == 0 || ticket.isExpired(now())) {
            throw invalid();
        }
        Meeting meeting = meetingRepository.findById(ticket.getMeetingId()).orElseThrow(SttTicketService::invalid);
        if (!meeting.is(MeetingStatus.IN_PROGRESS) || !meeting.isWriter(ticket.getUserId())) {
            throw invalid();
        }
        return new SttTicketOwnerResponse(ticket.getMeetingId(), ticket.getUserId());
    }

    /** 쓰지 않고 만료된 입장권 정리 */
    @Scheduled(fixedDelayString = "${nineouting.meeting.stt-ticket-cleanup-interval:10m}")
    @Transactional
    public void deleteExpired() {
        ticketRepository.deleteExpired(now());
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static BusinessException invalid() {
        return new BusinessException(MeetingErrorCode.INVALID_STT_TICKET);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
