package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingCalendarTransactionTest {
    private final MeetingRepository meetings = mock(MeetingRepository.class);
    private final WorkspaceClient workspace = mock(WorkspaceClient.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final MeetingService service = new MeetingService(meetings, mock(MeetingAttendeeRepository.class),
            mock(AgendaRepository.class), mock(PendingAgendaRepository.class), mock(MeetingAccess.class),
            workspace, mock(UserClient.class), mock(OutboxEventWriter.class), Clock.systemUTC(), transactions);

    @Test
    void workspaceCallDoesNotHoldDatabaseTransactionAndCalendarResponseIsPreserved() throws Exception {
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
        var folder = mock(AccessibleFolder.class);
        when(folder.folderId()).thenReturn(20L);
        when(folder.name()).thenReturn("Test folder");
        when(folder.workspaceName()).thenReturn("Test workspace");
        when(workspace.accessibleFolders(5L)).thenAnswer(invocation -> {
            assertFalse(active.get());
            verifyNoInteractions(transactions);
            return List.of(folder);
        });
        var from = LocalDate.of(2026, 11, 1);
        var to = LocalDate.of(2026, 11, 30);
        var meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(51L);
        when(meeting.getFolderId()).thenReturn(20L);
        when(meeting.getTitle()).thenReturn("Meeting");
        when(meetings.findCalendar(anyCollection(), eq(MeetingStatus.CANCELLED),
                eq(from.atStartOfDay()), eq(to.plusDays(1).atStartOfDay()))).thenAnswer(invocation -> {
            assertTrue(active.get());
            return List.of(meeting);
        });
        var annotation = MeetingService.class.getMethod("calendar", Long.class, LocalDate.class, LocalDate.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.NOT_SUPPORTED, annotation.propagation());

        var result = service.calendar(5L, from, to);

        assertEquals(1, result.size());
        assertEquals(51L, result.getFirst().meetingId());
        assertEquals("Test folder", result.getFirst().folderName());
        assertEquals("Test workspace", result.getFirst().workspaceName());
        verify(transactions).commit(any());
        assertFalse(active.get());
    }

    @Test
    void emptyAccessibleFoldersDoNotOpenDatabaseTransaction() {
        when(workspace.accessibleFolders(5L)).thenReturn(List.of());
        assertTrue(service.calendar(5L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)).isEmpty());
        verifyNoInteractions(transactions, meetings);
    }

    @Test
    void workspaceFailureDoesNotOpenDatabaseTransaction() {
        when(workspace.accessibleFolders(5L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,
                () -> service.calendar(5L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)));
        verifyNoInteractions(transactions, meetings);
    }
}
