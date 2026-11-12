package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.meeting.service.MaterialRagSync;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 서비스 간 주소 (밖으로 열지 않는다): ai-service → 사전 자료 넣기 결과 (RAG 명세 "완료 콜백", REQ-187) */
@RestController
@RequiredArgsConstructor
public class InternalRagController {

    private final MaterialRagSync materialRagSync;

    /** 지금 보낸 버전이 아닌 결과도 2xx로 답한다 (ai-service가 다시 보내지 않게) */
    @PostMapping("/internal/rag/ingest-result")
    public ResponseEntity<Void> receiveIngestResult(@RequestBody JsonNode body) {
        materialRagSync.receiveResult(body);
        return ResponseEntity.noContent().build();
    }
}
