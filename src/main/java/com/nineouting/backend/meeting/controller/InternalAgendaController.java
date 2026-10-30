package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.DraftSourceResponse;
import com.nineouting.backend.meeting.service.AgendaBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 안건 보드 서비스 간 주소 (밖으로 열지 않는다). ai-service docs/api/agenda-*.md.
 * 응답: 반영하면 204, 버전이 같거나 낮아 무시해도 204, 형식 오류 400, 없는 회의 404, 시작 전 회의 409.
 */
@RestController
@RequiredArgsConstructor
public class InternalAgendaController {

    private final AgendaBoardService agendaBoardService;

    /** ai-service → 줄별 안건 분류 (agenda-contract.md) */
    @PutMapping("/internal/meetings/{meetingId}/agenda-classification")
    public ResponseEntity<Void> receiveClassification(@PathVariable Long meetingId, @RequestBody JsonNode body) {
        agendaBoardService.receiveClassification(meetingId, body);
        return ResponseEntity.noContent().build();
    }

    /** ai-service → 안건 보드 (agenda-summary·status·opinions·offtopic·leftover·today-summary-contract.md) */
    @PutMapping("/internal/meetings/{meetingId}/agenda-board")
    public ResponseEntity<Void> receiveBoard(@PathVariable Long meetingId, @RequestBody JsonNode body) {
        agendaBoardService.receiveBoard(meetingId, body);
        return ResponseEntity.noContent().build();
    }

    /** minutes-service → AI 회의록 초안 재료 (ai-service minutes-draft-contract.md 5.1·5.2) */
    @GetMapping("/internal/meetings/{meetingId}/draft-source")
    public ApiResponse<DraftSourceResponse> draftSource(@PathVariable Long meetingId) {
        return ApiResponse.ok(agendaBoardService.draftSource(meetingId));
    }
}
