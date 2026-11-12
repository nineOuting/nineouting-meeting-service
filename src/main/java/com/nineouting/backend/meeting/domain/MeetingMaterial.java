package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** REQ-067 회의 사전 자료. 파일은 S3에 있고 여기에는 위치(storageKey)와 이름만 둔다 */
@Getter
@Entity
@Table(name = "meeting_materials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingMaterial {

    public enum Status {
        /** 업로드 주소를 받았지만 아직 다 올렸다고 알리지 않음 (목록에 안 보인다) */
        UPLOADING,
        READY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** S3가 파일마다 주는 ETag (따옴표를 뗀 값). 검색용 저장소에 넣을 때의 버전(docVersion) */
    @Column(length = 200)
    private String etag;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_status", length = 20)
    private RagStatus ragStatus;

    @Column(name = "rag_error_code", length = 40)
    private String ragErrorCode;

    @Column(name = "rag_attempts", nullable = false)
    private int ragAttempts;

    @Column(name = "rag_next_attempt_at")
    private LocalDateTime ragNextAttemptAt;

    @Column(name = "rag_sent_at")
    private LocalDateTime ragSentAt;

    public MeetingMaterial(Long meetingId, String fileName, String contentType, long sizeBytes, String storageKey,
                           Long uploadedBy, LocalDateTime now) {
        this.meetingId = meetingId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.status = Status.UPLOADING;
        this.createdAt = now;
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    /** 실제로 올라간 크기로 바꾸고 목록에 보이게 한다 */
    public void ready(long actualSize, String etag, LocalDateTime now) {
        this.sizeBytes = actualSize;
        this.status = Status.READY;
        this.etag = etag;
        ragPending(now);
    }

    // ---- 검색용 저장소 (REQ-187) ----

    /** 처음부터 다시 보낸다 */
    public void ragPending(LocalDateTime now) {
        this.ragStatus = RagStatus.PENDING;
        this.ragErrorCode = null;
        this.ragAttempts = 0;
        this.ragNextAttemptAt = now;
    }

    /** 배포 전에 올라간 자료는 ETag가 없어 보낼 때 채운다 */
    public void keepEtag(String etag) {
        this.etag = etag;
    }

    public void ragAccepted(LocalDateTime now) {
        this.ragStatus = RagStatus.ACCEPTED;
        this.ragSentAt = now;
    }

    public void ragReady() {
        this.ragStatus = RagStatus.READY;
        this.ragErrorCode = null;
    }

    public void ragFailed(String errorCode) {
        this.ragStatus = RagStatus.FAILED;
        this.ragErrorCode = errorCode;
    }

    /** 다시 보낼 일. 횟수를 넘으면 실패로 둔다 */
    public void ragRetry(String errorCode, LocalDateTime nextAttemptAt, int maxAttempts) {
        this.ragAttempts++;
        if (ragAttempts >= maxAttempts) {
            ragFailed(errorCode);
            return;
        }
        this.ragStatus = RagStatus.PENDING;
        this.ragErrorCode = errorCode;
        this.ragNextAttemptAt = nextAttemptAt;
    }
}
