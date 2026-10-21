package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.meeting.domain.ConnectionTicket;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.TicketPurpose;
import com.nineouting.backend.meeting.dto.SttTicketOwnerResponse;
import com.nineouting.backend.meeting.dto.TicketResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.ConnectionTicketRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
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
 * WebSocket 연결용 1회용 입장권 (docs/services/ai-integration.md 2.1, meeting-service.md 4.3.2).
 * <ul>
 *   <li>음성 연결(STT): 회의록 작성자의 브라우저만. stt-service가 /internal로 확인한다 (REQ-079)</li>
 *   <li>회의방: 참석자 누구나. 회의방 WebSocket 연결을 받을 때 meeting-service가 확인한다 (REQ-079, 083)</li>
 * </ul>
 * 둘 다 진행 중인 회의에서만 주고, 확인하면 지운다. 그사이 회의가 끝났거나 권한이 바뀌었어도 거절한다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionTicketService {

    static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final int TOKEN_BYTES = 32;

    private final ConnectionTicketRepository ticketRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final MeetingAccess access;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 음성 연결 입장권. 회의록 작성자, 진행 중 */
    @Transactional
    public TicketResponse issueStt(Long meetingId, Long userId) {
        access.requireWriterInProgress(meetingId, userId);
        return issue(TicketPurpose.STT, meetingId, userId);
    }

    /** 회의방 입장권. 참석자, 진행 중 */
    @Transactional
    public TicketResponse issueRoom(Long meetingId, Long userId) {
        Meeting meeting = access.requireAttendee(meetingId, userId);
        if (!meeting.is(MeetingStatus.IN_PROGRESS)) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_IN_PROGRESS);
        }
        return issue(TicketPurpose.ROOM, meetingId, userId);
    }

    /** stt-service가 음성 연결을 받을 때 확인한다 */
    @Transactional
    public SttTicketOwnerResponse consumeStt(String token) {
        ConnectionTicket ticket = consume(token, TicketPurpose.STT);
        Meeting meeting = requireInProgress(ticket.getMeetingId());
        if (!meeting.isWriter(ticket.getUserId())) {
            throw invalid();
        }
        return new SttTicketOwnerResponse(ticket.getMeetingId(), ticket.getUserId());
    }

    /** 회의방 연결을 받을 때 확인한다. 주소의 회의와 입장권의 회의가 같아야 한다 */
    @Transactional
    public Long consumeRoom(String token, Long meetingId) {
        ConnectionTicket ticket = consume(token, TicketPurpose.ROOM);
        if (!ticket.getMeetingId().equals(meetingId)) {
            throw invalid();
        }
        requireInProgress(meetingId);
        if (!attendeeRepository.existsByMeetingIdAndUserId(meetingId, ticket.getUserId())) {
            throw invalid();
        }
        return ticket.getUserId();
    }

    /** 쓰지 않고 만료된 입장권 정리 */
    @Scheduled(fixedDelayString = "${nineouting.meeting.stt-ticket-cleanup-interval:10m}")
    @Transactional
    public void deleteExpired() {
        ticketRepository.deleteExpired(now());
    }

    private TicketResponse issue(TicketPurpose purpose, Long meetingId, Long userId) {
        String token = newToken();
        ticketRepository.save(new ConnectionTicket(token, purpose, meetingId, userId, now().plus(TICKET_TTL)));
        return new TicketResponse(token, TICKET_TTL.toSeconds());
    }

    /** 지우면서 확인한다. 같은 입장권을 동시에 써도 한쪽만 통과한다 */
    private ConnectionTicket consume(String token, TicketPurpose purpose) {
        ConnectionTicket ticket = ticketRepository.findById(token).orElseThrow(ConnectionTicketService::invalid);
        if (ticketRepository.deleteByToken(token) == 0 || ticket.isExpired(now()) || ticket.getPurpose() != purpose) {
            throw invalid();
        }
        return ticket;
    }

    private Meeting requireInProgress(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(ConnectionTicketService::invalid);
        if (!meeting.is(MeetingStatus.IN_PROGRESS)) {
            throw invalid();
        }
        return meeting;
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
