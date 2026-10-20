package com.nineouting.backend.meeting.client;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ai-service 호출 (docs/services/ai-integration.md 2.4, ai-service docs/api/input-contract.md). 타임아웃은 AiClientConfig에서 정한다.
 * 응답이 2xx면 받은 것으로 본다 (이미 받은 묶음 duplicate, 종료 뒤 ignored도 200이라 다시 보내지 않는다).
 * 408·429·5xx·연결 실패는 다시 보낼 일(AiServiceException), 그 밖의 4xx는 다시 보내도 소용없는 거절(AiRejectedException).
 * 호출 시간·실패는 RestClient 관측으로 http.client.requests 지표에 남는다.
 */
public class AiServiceClient {

    private static final String EXPECTED_BATCH = "expectedBatch";
    /** 로그에 남길 거절 응답 본문 길이 */
    private static final int MAX_BODY_IN_LOG = 300;

    private final RestClient restClient;

    public AiServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** GET /health가 2xx면 정상 (REQ-111 AI 일시 중단 감지) */
    public boolean isHealthy() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /** 회의 알림 (시작·멈춤·재개·종료) */
    public void sendEvent(Long meetingId, Object event) {
        call(() -> restClient.post()
                .uri("/internal/meetings/{meetingId}/events", meetingId)
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
                    .uri("/internal/meetings/{meetingId}/batches", meetingId)
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
            throw clientError("줄 묶음", e);
        } catch (RestClientException e) {
            throw new AiServiceException("ai-service 호출 실패: " + e.getMessage(), e);
        }
    }

    private static void call(Runnable request) {
        try {
            request.run();
        } catch (HttpClientErrorException e) {
            throw clientError("회의 알림", e);
        } catch (RestClientException e) {
            throw new AiServiceException("ai-service 호출 실패: " + e.getMessage(), e);
        }
    }

    /** 408(시간 초과)·429(너무 많은 요청)는 잠시 뒤 다시 보내면 되고, 그 밖의 4xx는 거절이다 */
    private static AiServiceException clientError(String what, HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String message = "ai-service가 " + what + "을 거절했습니다: " + e.getStatusCode() + " " + body(e);
        if (status == HttpStatus.REQUEST_TIMEOUT.value() || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new AiServiceException(message, e);
        }
        return new AiRejectedException(message, e);
    }

    private static String body(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return body.length() > MAX_BODY_IN_LOG ? body.substring(0, MAX_BODY_IN_LOG) + "…" : body;
    }
}
