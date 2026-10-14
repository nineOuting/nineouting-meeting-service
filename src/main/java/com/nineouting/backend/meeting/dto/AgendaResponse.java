package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.AgendaStatus;

public record AgendaResponse(Long agendaId, int position, String title, String decisionPoint, AgendaStatus status,
                             Long pendingAgendaId, boolean addedDuringMeeting) {

    public static AgendaResponse from(Agenda agenda) {
        return new AgendaResponse(agenda.getId(), agenda.getPosition(), agenda.getTitle(), agenda.getDecisionPoint(),
                agenda.getStatus(), agenda.getPendingAgendaId(), agenda.isAddedDuringMeeting());
    }
}
