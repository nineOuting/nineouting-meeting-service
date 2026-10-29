package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;

/** "확인 필요" 표시를 확인함 */
public record AgendaItemConfirmRequest(@NotNull(message = "agendaId를 보내 주세요") Long agendaId) {
}
