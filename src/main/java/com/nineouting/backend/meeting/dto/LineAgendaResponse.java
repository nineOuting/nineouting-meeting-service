package com.nineouting.backend.meeting.dto;

/**
 * 전사 줄의 안건 (REQ-089). agendaId가 null이면 해당 없음.
 *
 * @param corrected 회의록 작성자가 고친 줄 (자동 분류가 덮어쓰지 않는다)
 */
public record LineAgendaResponse(int seq, Long agendaId, boolean corrected) {
}
