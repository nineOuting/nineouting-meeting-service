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
    public void ready(long actualSize) {
        this.sizeBytes = actualSize;
        this.status = Status.READY;
    }
}
