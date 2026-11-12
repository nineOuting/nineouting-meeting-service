package com.nineouting.backend.meeting.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * minutes-service 내부 주소: 폴더의 작성 완료 회의록 번호 (최근 회의부터, 쓰레기통 제외). REQ-185 챗봇이 찾을 회의록.
 * 연결이 안 되면 AiServiceException (챗봇 답 실패로 보여 준다).
 */
public class MinutesDocsClient {

    private static final ParameterizedTypeReference<List<Long>> IDS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public MinutesDocsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<Long> confirmedMinutesIds(Long folderId) {
        try {
            List<Long> ids = restClient.get().uri("/internal/minutes/rag-docs?folderId={folderId}", folderId)
                    .retrieve()
                    .body(IDS);
            return ids == null ? List.of() : ids;
        } catch (RestClientException e) {
            throw new AiServiceException("minutes-service 회의록 목록 조회 실패: " + e.getMessage(), e);
        }
    }
}
