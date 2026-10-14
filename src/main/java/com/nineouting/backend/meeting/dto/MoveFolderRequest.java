package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;

/** 회의를 다른 폴더로 옮기기 (REQ-043) */
public record MoveFolderRequest(@NotNull(message = "옮길 폴더를 선택해 주세요") Long folderId) {
}
