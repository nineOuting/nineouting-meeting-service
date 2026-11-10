package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.MaterialResponse;
import com.nineouting.backend.meeting.dto.MaterialUploadRequest;
import com.nineouting.backend.meeting.dto.MaterialUploadResponse;
import com.nineouting.backend.meeting.service.MaterialService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REQ-067 회의 사전 자료: 업로드 주소 받기 → 브라우저가 S3에 PUT → complete → 목록, 내려받기, 삭제 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping
    public ResponseEntity<ApiResponse<MaterialUploadResponse>> prepare(@CurrentUser AuthenticatedUser user,
                                                                       @PathVariable Long meetingId,
                                                                       @Valid @RequestBody MaterialUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(materialService.prepare(meetingId, user.userId(), request)));
    }

    @PostMapping("/{materialId}/complete")
    public ApiResponse<MaterialResponse> complete(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                                  @PathVariable Long materialId) {
        return ApiResponse.ok(materialService.complete(meetingId, user.userId(), materialId));
    }

    @GetMapping
    public ApiResponse<List<MaterialResponse>> list(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        return ApiResponse.ok(materialService.list(meetingId, user.userId()));
    }

    @DeleteMapping("/{materialId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                    @PathVariable Long materialId) {
        materialService.delete(meetingId, user.userId(), materialId);
        return ApiResponse.ok();
    }
}
