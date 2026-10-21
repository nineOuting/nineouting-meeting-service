package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 다음 회의로 넘기기 (REQ-094): 고른 안건을 폴더의 남은 안건으로 저장한다 */
public record CarryOverRequest(@NotEmpty(message = "넘길 안건을 선택해 주세요") List<Long> agendaIds) {
}
