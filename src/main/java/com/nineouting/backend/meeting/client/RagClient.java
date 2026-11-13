package com.nineouting.backend.meeting.client;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * ai-service RAG 호출 (RAG 명세 2026-11-12, 회의 중 챗봇 명세 v0.2). 서버 간 인증은 지금 없다 (내부망만).
 * 400, 409는 다시 보내도 소용없는 거절(AiRejectedException), 그 밖(404, 408, 429, 5xx, 연결 실패)은 다시 보낼 일
 * (AiServiceException). 404는 ai-service에 RAG가 꺼져 있을 때다 (RAG_DATABASE_URL 없음). 켜지면 다시 보낸다.
 * 질문, 답, 회의 내용은 로그에 남기지 않는다 (챗봇 명세 7장).
 */
public class RagClient {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String PREFIX = "/internal/rag";
    /** 사전 자료 저장소를 허용하지 않을 때 ai-service가 detail에 넣는 글 */
    private static final java.util.List<String> UPLOAD_NOT_CONFIGURED = java.util.List.of("RAG_UPLOAD_ALLOWED_HOSTS",
            "허용한 저장소가 아님");

    private final RestClient ingestClient;
    private final RestClient chatClient;

    public RagClient(RestClient ingestClient, RestClient chatClient) {
        this.ingestClient = ingestClient;
        this.chatClient = chatClient;
    }

    /** 문서 넣기. 응답의 status(accepted, ignored)를 돌려준다 */
    public String ingest(Object body) {
        JsonNode response = call(() -> ingestClient.post().uri(PREFIX + "/documents")
                .header(REQUEST_ID_HEADER, requestId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return response == null ? "accepted" : response.path("status").asString("accepted");
    }

    /** 상태 조회. 넣은 적 없으면 빈 값 */
    public Optional<JsonNode> status(String sourceType, Long docId) {
        try {
            return Optional.ofNullable(call(() -> ingestClient.get()
                    .uri(PREFIX + "/documents/{sourceType}/{docId}", sourceType, docId)
                    .header(REQUEST_ID_HEADER, requestId())
                    .retrieve()
                    .body(JsonNode.class)));
        } catch (AiServiceException e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** 문서 지우기 (없는 문서여도 204) */
    public void delete(String sourceType, Long docId) {
        call(() -> ingestClient.delete()
                .uri(PREFIX + "/documents/{sourceType}/{docId}", sourceType, docId)
                .header(REQUEST_ID_HEADER, requestId())
                .retrieve()
                .toBodilessEntity());
    }

    /** 회의 중 챗봇 질문 하나. 최대 60초 걸린다 */
    public JsonNode chat(Object body) {
        return call(() -> chatClient.post().uri(PREFIX + "/chat")
                .header(REQUEST_ID_HEADER, requestId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }

    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String message = "ai-service RAG가 거절했습니다: " + e.getStatusCode() + " " + errorOf(e);
            if (status == HttpStatus.BAD_REQUEST.value() && isUploadNotConfigured(e)) {
                // 요청이 틀린 것이 아니라 ai-service에 사전 자료 저장소 허용(RAG_UPLOAD_ALLOWED_HOSTS)이 아직 없다. 설정되면 다시 보낸다
                throw new AiServiceException(message + " (사전 자료 저장소 허용 설정 전)", e);
            }
            if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.CONFLICT.value()) {
                throw new AiRejectedException(message, e);
            }
            throw new AiServiceException(message, e);
        } catch (RestClientException e) {
            throw new AiServiceException("ai-service RAG 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * ai-service가 사전 자료 저장소를 허용하지 않아 거절했는지 (rag/download.py check_file_url의 detail).
     * error 코드는 다른 400과 같은 INVALID_INPUT이라 detail로 가른다. 따로 코드가 생기면 그것으로 바꾼다
     */
    static boolean isUploadNotConfigured(HttpClientErrorException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            String detail = body == null ? "" : body.path("detail").asString("");
            return UPLOAD_NOT_CONFIGURED.stream().anyMatch(detail::contains);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 오류 본문의 error 코드만 (detail에는 사용자 글이 들어갈 수 있어 남기지 않는다) */
    private static String errorOf(HttpClientErrorException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            return body == null ? "" : body.path("error").asString("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
