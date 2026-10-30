package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.AgendaStatus;
import java.util.List;

public record AgendaResponse(Long agendaId, int position, String title, List<DecisionPointResponse> decisionPoints,
                             AgendaStatus status, Long pendingAgendaId, boolean addedDuringMeeting) {

    public static AgendaResponse from(Agenda agenda) {
        return new AgendaResponse(agenda.getId(), agenda.getPosition(), agenda.getTitle(),
                agenda.getDecisionPoints().stream().map(DecisionPointResponse::from).toList(), agenda.getStatus(), agenda.getPendingAgendaId(), agenda.isAddedDuringMeeting());
    }
}
