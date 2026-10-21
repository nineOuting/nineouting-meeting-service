package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회의 안건 (REQ-065~066). position 순서로 보여 준다 (REQ-069).
 * pendingAgendaId: 남은 안건에서 불러왔거나(REQ-068), 이 안건을 다음 회의로 넘겼을 때(REQ-094) 그 남은 안건.
 */
@Getter
@Entity
@Table(name = "agendas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "decision_point", nullable = false, length = 500)
    private String decisionPoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgendaStatus status;

    @Column(name = "pending_agenda_id")
    private Long pendingAgendaId;

    @Column(name = "added_during_meeting", nullable = false)
    private boolean addedDuringMeeting;

    public Agenda(Long meetingId, int position, String title, String decisionPoint, Long pendingAgendaId,
                  boolean addedDuringMeeting) {
        this.meetingId = meetingId;
        this.position = position;
        this.title = title;
        this.decisionPoint = decisionPoint;
        this.pendingAgendaId = pendingAgendaId;
        this.addedDuringMeeting = addedDuringMeeting;
        this.status = AgendaStatus.NOT_STARTED;
    }

    public void linkPendingAgenda(Long pendingAgendaId) {
        this.pendingAgendaId = pendingAgendaId;
    }
}
