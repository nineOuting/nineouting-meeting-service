package com.nineouting.backend.meeting.client;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ai-service 호출 (docs/services/ai-integration.md 2.4). 타임아웃은 AiClientConfig에서 정한다.
 * 호출 시간·실패는 RestClient 관측으로 http.client.requests 지표에 남는다.
 */
public class AiServiceClient {

    private static final String EXPECTED_BATCH = "expectedBatch";

    private final RestClient restClient;

    public AiServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 회의 알림 (시작·멈춤·재개·종료) */
    public void sendEvent(Long meetingId, Object event) {
        call(() -> restClient.post()
                .uri("/meetings/{meetingId}/events", meetingId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * 줄 묶음. 저장해 둔 그대로 다시 보낼 수 있게 JSON 문자열로 받는다.
     *
     * @return ai-service가 빠진 묶음 번호를 알려 주면(409) 그 번호, 받았으면 null
     */
    public Integer sendLines(Long meetingId, String payloadJson) {
        try {
            restClient.post()
                    .uri("/meetings/{meetingId}/lines", meetingId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadJson)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                Map<?, ?> body = e.getResponseBodyAs(Map.class);
                if (body != null && body.get(EXPECTED_BATCH) instanceof Number expected) {
                    return expected.intValue();
                }
            }
            throw new AiServiceException("ai-service가 줄 묶음을 거절했습니다: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AiServiceException("ai-service 호출 실패: " + e.getMessage(), e);
        }
    }

    private static void call(Runnable request) {
        try {
            request.run();
        } catch (RestClientException e) {
            throw new AiServiceException("ai-service 호출 실패: " + e.getMessage(), e);
        }
    }
}
