package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.client.AiRejectedException;
import com.nineouting.backend.meeting.client.AiServiceException;
import com.nineouting.backend.meeting.client.RagClient;
import com.nineouting.backend.meeting.config.AiFeedProperties;
import com.nineouting.backend.meeting.config.RagProperties;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingMaterial;
import com.nineouting.backend.meeting.domain.RagStatus;
import com.nineouting.backend.meeting.repository.MeetingMaterialRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * REQ-187 사전 자료를 ai-service 검색용 저장소에 넣는다 (docs/services/rag-chatbot.md 3장, RAG 명세 2026-11-12).
 * 보관함 표 없이 자료 행의 rag_status로 보내고 다시 보낸다. 여러 파드가 같은 자료를 보내지 않게 행을 잠근다.
 * 사용자가 고칠 수 없는 실패(글이 없음, 받지 않는 형식, 암호, 너무 큼)와 거절(400, 409)은 다시 보내지 않는다.
 * ai-service가 안 되거나 RAG가 꺼져 있으면(404) 실패로 굳히지 않고 간격을 늘려(최대 약 30분) 계속 다시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialRagSync {

    public static final String SOURCE_TYPE = "upload";
    private static final int MAX_PER_RUN = 10;
    private static final int MAX_TITLE = 500;
    /** ai-service가 파일을 받을 주소의 유효 시간. 만료되면 url_expired로 알려 오고 새 주소로 다시 보낸다 */
    private static final Duration FILE_URL_TTL = Duration.ofMinutes(30);
    /** 다시 보내면 나을 수 있는 실패 (RAG 명세 "백엔드가 할 일") */
    private static final Set<String> RETRYABLE = Set.of("download_failed", "url_expired", "internal_error");
    private static final Set<String> UNREADABLE = Set.of("no_text", "unsupported_format", "encrypted");
    private static final String TOO_LARGE = "too_large";
    private static final String REJECTED = "rejected";
    private static final String UNAVAILABLE = "unavailable";

    private final MeetingMaterialRepository materialRepository;
    private final MeetingRepository meetingRepository;
    private final MaterialStorage storage;
    private final RagClient ragClient;
    private final AiFeedProperties aiProperties;
    private final RagProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${nineouting.meeting.rag.send-interval:5s}")
    public void run() {
        if (!aiProperties.enabled()) {
            return;
        }
        for (int i = 0; i < MAX_PER_RUN; i++) {
            if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> sendNext()))) {
                break;
            }
        }
        for (int i = 0; i < MAX_PER_RUN; i++) {
            if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> checkNextStale()))) {
                break;
            }
        }
    }

    /** ai-service 완료 콜백 (POST /internal/rag/ingest-result). 지금 보낸 버전이 아니면 무시한다 */
    public void receiveResult(JsonNode body) {
        if (!SOURCE_TYPE.equals(body.path("sourceType").asString(""))) {
            return;
        }
        long docId = body.path("docId").asLong(0);
        String version = body.path("docVersion").asString("");
        transactionTemplate.executeWithoutResult(status -> materialRepository.findForUpdate(docId)
                .filter(material -> version.equals(material.getEtag()))
                .filter(material -> material.getRagStatus() == RagStatus.ACCEPTED)
                .ifPresent(material -> apply(material, body.path("status").asString(""),
                        body.path("errorCode").asString(null))));
    }

    /** 자료를 지웠다. 지운 트랜잭션이 끝난 뒤 검색용 저장소에서도 지운다. 실패해도 지우기를 막지 않는다 */
    public void deleteQuietly(Long materialId) {
        if (!aiProperties.enabled()) {
            return;
        }
        Runnable delete = () -> {
            try {
                ragClient.delete(SOURCE_TYPE, materialId);
            } catch (AiServiceException e) {
                log.warn("검색용 저장소에서 사전 자료를 지우지 못했습니다 (materialId={}): {}", materialId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    /** 화면에 보여 줄 검색 상태. 챗봇이 꺼져 있으면 null */
    public String searchStatus(MeetingMaterial material) {
        if (!aiProperties.enabled() || material.getRagStatus() == null) {
            return null;
        }
        return switch (material.getRagStatus()) {
            case PENDING, ACCEPTED -> "PREPARING";
            case READY -> "READY";
            case FAILED -> UNREADABLE.contains(material.getRagErrorCode()) ? "UNREADABLE"
                    : TOO_LARGE.equals(material.getRagErrorCode()) ? "TOO_LARGE" : "FAILED";
        };
    }

    private boolean sendNext() {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<MeetingMaterial> due = materialRepository.lockNextRagDue(now);
        if (due.isEmpty()) {
            return false;
        }
        MeetingMaterial material = due.get();
        Optional<Meeting> meeting = meetingRepository.findById(material.getMeetingId());
        if (meeting.isEmpty()) {
            material.ragFailed(REJECTED);
            return true;
        }
        if (material.getEtag() == null) {
            Optional<MaterialStorage.StoredFile> file = storage.head(material.getStorageKey());
            if (file.isEmpty()) {
                material.ragFailed("download_failed");
                return true;
            }
            material.keepEtag(file.get().etag());
        }
        try {
            ragClient.ingest(body(material, meeting.get()));
            material.ragAccepted(now);
        } catch (AiRejectedException e) {
            material.ragFailed(REJECTED);
            log.warn("ai-service가 사전 자료 넣기를 거절했습니다 (materialId={}): {}", material.getId(), e.getMessage());
        } catch (AiServiceException e) {
            material.ragRetryLater(UNAVAILABLE, now.plus(backoff(material)));
            log.warn("사전 자료를 검색용 저장소에 보내지 못해 나중에 다시 보냅니다 (materialId={}, {}회째): {}",
                    material.getId(), material.getRagAttempts(), e.getMessage());
        }
        return true;
    }

    /** 접수됐는데 콜백이 없으면 상태를 묻는다. 넣은 적 없다고 하면 다시 보낸다 */
    private boolean checkNextStale() {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<MeetingMaterial> stale = materialRepository.lockNextRagStale(now.minus(properties.acceptedTimeout()));
        if (stale.isEmpty()) {
            return false;
        }
        MeetingMaterial material = stale.get();
        try {
            Optional<JsonNode> result = ragClient.status(SOURCE_TYPE, material.getId());
            if (result.isEmpty() || !material.getEtag().equals(result.get().path("docVersion").asString(""))) {
                material.ragPending(now);
            } else {
                apply(material, result.get().path("status").asString(""), result.get().path("errorCode").asString(null));
                if (material.getRagStatus() == RagStatus.ACCEPTED) {
                    material.ragAccepted(now);
                }
            }
        } catch (AiServiceException e) {
            material.ragAccepted(now);
            log.warn("사전 자료 검색 상태를 확인하지 못했습니다 (materialId={}): {}", material.getId(), e.getMessage());
        }
        return true;
    }

    private void apply(MeetingMaterial material, String status, String errorCode) {
        if ("ready".equals(status)) {
            material.ragReady();
        } else if ("failed".equals(status)) {
            if (RETRYABLE.contains(errorCode)) {
                material.ragRetry(errorCode, LocalDateTime.now(clock), properties.maxAttempts());
            } else {
                material.ragFailed(errorCode == null ? "internal_error" : errorCode);
            }
        }
    }

    private Map<String, Object> body(MeetingMaterial material, Meeting meeting) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("docId", material.getId());
        body.put("docVersion", material.getEtag());
        body.put("workspaceId", meeting.getWorkspaceId());
        body.put("sourceType", SOURCE_TYPE);
        String title = material.getFileName();
        body.put("title", title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title);
        body.put("fileUrl", storage.fileUrlForAi(material.getStorageKey(), FILE_URL_TTL));
        body.put("callbackUrl", properties.callbackBaseUrl() + "/internal/rag/ingest-result");
        return body;
    }

    private Duration backoff(MeetingMaterial material) {
        return properties.retryDelay().multipliedBy(1L << Math.min(material.getRagAttempts(), 6));
    }
}
