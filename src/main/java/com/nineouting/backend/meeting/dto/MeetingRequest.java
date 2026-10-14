package com.nineouting.backend.meeting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회의 생성(REQ-056~067)·수정(REQ-115). 시각은 한국 시간.
 *
 * @param attendeeUserIds   참석자. 만든 사람은 없어도 들어간다
 * @param agendas           배열 순서 = 안건 순서 (REQ-065)
 * @param previousMeetingId 이어서 만들기(REQ-047)의 이전 회의. 생성할 때만 쓴다
 */
public record MeetingRequest(
        @NotBlank(message = "회의명을 입력해 주세요")
        @Size(max = 100, message = "회의명은 100자 이하로 입력해 주세요")
        String title,
        @NotNull(message = "시작 시간을 선택해 주세요")
        LocalDateTime startAt,
        @NotNull(message = "종료 예정 시간을 선택해 주세요")
        LocalDateTime endAt,
        @Size(max = 100, message = "장소는 100자 이하로 입력해 주세요")
        String location,
        List<Long> attendeeUserIds,
        @NotNull(message = "회의록 작성자를 선택해 주세요")
        Long writerUserId,
        @NotEmpty(message = "안건을 1개 이상 등록해 주세요")
        List<@Valid AgendaRequest> agendas,
        Long previousMeetingId
) {
    public MeetingRequest {
        title = title == null ? null : title.trim();
        location = location == null || location.isBlank() ? null : location.trim();
        attendeeUserIds = attendeeUserIds == null ? List.of() : attendeeUserIds;
    }
}
