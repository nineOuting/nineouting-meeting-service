package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** REQ-067 사전 자료 업로드 주소 요청. 크기는 브라우저가 알려 준 값이고, 다 올린 뒤 서버가 다시 확인한다 */
public record MaterialUploadRequest(
        @NotBlank(message = "파일 이름이 없습니다")
        @Size(max = 255, message = "파일 이름은 255자 이하여야 합니다")
        String fileName,
        @Size(max = 100, message = "파일 형식이 너무 깁니다")
        String contentType,
        @Positive(message = "빈 파일은 올릴 수 없습니다")
        long size
) {
    public MaterialUploadRequest {
        fileName = fileName == null ? null : fileName.trim();
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
    }
}
