package com.nineouting.backend.meeting.dto;

/** 줄의 안건 고치기 (REQ-089). agendaId가 null이면 해당 없음 */
public record LineAgendaRequest(Long agendaId) {
}
