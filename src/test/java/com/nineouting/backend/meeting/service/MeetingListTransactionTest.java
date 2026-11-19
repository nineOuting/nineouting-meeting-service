package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingAttendee;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.repository.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingListTransactionTest {
    @Test
    void externalCallsRunOutsideDatabaseTransactionAndResponseIsPreserved() throws Exception {
        var meetings = mock(MeetingRepository.class);
        var attendees = mock(MeetingAttendeeRepository.class);
        var access = mock(MeetingAccess.class);
        var users = mock(UserClient.class);
        var transactions = mock(PlatformTransactionManager.class);
        var active = new AtomicBoolean();
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertTrue(definition.isReadOnly());
            assertFalse(active.getAndSet(true));
            return new SimpleTransactionStatus();
        });
        doAnswer(invocation -> {
            assertTrue(active.getAndSet(false));
            return null;
        }).when(transactions).commit(any());
        var service = new MeetingService(meetings, attendees, mock(AgendaRepository.class),
                mock(PendingAgendaRepository.class), access, mock(WorkspaceClient.class), users,
                mock(OutboxEventWriter.class), Clock.systemUTC(), transactions);
        var annotation = MeetingService.class
                .getMethod("list", Long.class, Long.class, int.class, int.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.NOT_SUPPORTED, annotation.propagation());
        when(access.requireFolderAccess(20L, 1L)).thenAnswer(invocation -> {
            assertFalse(active.get());
            verify(transactions, never()).getTransaction(any());
            return null;
        });
        Meeting meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(51L);
        when(meeting.getWriterUserId()).thenReturn(7L);
        when(meeting.getWorkspaceId()).thenReturn(9L);
        when(meetings.findByFolderIdAndStatusNotAndDeletedAtIsNull(
                eq(20L), eq(MeetingStatus.CANCELLED), any(Pageable.class))).thenAnswer(invocation -> {
            assertTrue(active.get());
            Pageable pageable = invocation.getArgument(2);
            assertEquals(0, pageable.getPageNumber());
            assertEquals(20, pageable.getPageSize());
            assertTrue(pageable.getSort().getOrderFor("startAt").isDescending());
            assertTrue(pageable.getSort().getOrderFor("id").isDescending());
            return new PageImpl<>(List.of(meeting), pageable, 1);
        });
        when(attendees.findByMeetingIdInOrderById(List.of(51L))).thenAnswer(invocation -> {
            assertTrue(active.get());
            return List.of(new MeetingAttendee(51L, 8L));
        });
        when(users.findUsers(anyCollection(), eq(9L))).thenAnswer(invocation -> {
            assertFalse(active.get());
            verify(transactions).commit(any());
            return Map.of(7L, new UserSummary(7L, "Writer", null, null),
                    8L, new UserSummary(8L, "Attendee", null, null));
        });

        var response = service.list(20L, 1L, 0, 20);

        assertEquals(1, response.totalCount());
        assertFalse(response.hasNext());
        assertEquals(51L, response.items().getFirst().meetingId());
        assertEquals("Writer", response.items().getFirst().writer().name());
        assertEquals("Attendee", response.items().getFirst().attendees().getFirst().name());
        verify(users).findUsers(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(7L, 8L))), eq(9L));
        assertFalse(active.get());
    }
}
