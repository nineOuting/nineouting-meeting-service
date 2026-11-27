package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.service.MaterialRagSync;
import com.nineouting.backend.meeting.service.MeetingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 주소 (밖으로 열지 않는다): admin-service → 운영 조치 (REQ-217) */
@RestController
@RequiredArgsConstructor
public class InternalAdminController {

    private final MeetingProgressService progressService;
    private final MaterialRagSync materialRagSync;

    /** 멈춘 회의 마무리. 진행 중이 아니면 409 */
    @PostMapping("/internal/admin/meetings/{meetingId}/finish")
    public ResponseEntity<Void> finish(@PathVariable Long meetingId) {
        progressService.finishByAdmin(meetingId);
        return ResponseEntity.noContent().build();
    }

    /** 실패한 사전 자료를 검색용 저장소에 다시 보낸다. 실패한 자료가 아니면 404 */
    @PostMapping("/internal/admin/materials/{materialId}/rag-retry")
    public ResponseEntity<Void> retryMaterialRag(@PathVariable Long materialId) {
        if (!materialRagSync.retryByAdmin(materialId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }
}
