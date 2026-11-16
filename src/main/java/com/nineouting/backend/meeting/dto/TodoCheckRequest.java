package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;

/** 할 일 완료 체크 (true) 또는 풀기 (false) */
public record TodoCheckRequest(@NotNull(message = "완료 여부를 보내 주세요") Boolean done) {
}
