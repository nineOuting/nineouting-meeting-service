package com.nineouting.backend.meeting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회의 안건 (REQ-065~066). position 순서로 보여 준다 (REQ-069). 결정할 사항은 여러 개다.
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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "agenda_id", nullable = false)
    @OrderBy("position")
    private List<AgendaDecisionPoint> decisionPoints = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgendaStatus status;

    @Column(name = "pending_agenda_id")
    private Long pendingAgendaId;

    @Column(name = "added_during_meeting", nullable = false)
    private boolean addedDuringMeeting;

    public Agenda(Long meetingId, int position, String title, List<String> decisionPoints, Long pendingAgendaId,
                  boolean addedDuringMeeting) {
        this.meetingId = meetingId;
        this.position = position;
        this.title = title;
        for (int index = 0; index < decisionPoints.size(); index++) {
            this.decisionPoints.add(new AgendaDecisionPoint(index, decisionPoints.get(index)));
        }
        this.pendingAgendaId = pendingAgendaId;
        this.addedDuringMeeting = addedDuringMeeting;
        this.status = AgendaStatus.NOT_STARTED;
    }

    public List<String> decisionPointContents() {
        return decisionPoints.stream().map(AgendaDecisionPoint::getContent).toList();
    }

    public void linkPendingAgenda(Long pendingAgendaId) {
        this.pendingAgendaId = pendingAgendaId;
    }
}
