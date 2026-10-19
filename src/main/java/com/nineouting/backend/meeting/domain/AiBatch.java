package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ai-service가 받은 줄 묶음. 빠진 번호를 알려 오면(409) 보낸 그대로 다시 보낸다 */
@Getter
@Entity
@Table(name = "ai_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private int batch;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public AiBatch(Long meetingId, int batch, String payload, LocalDateTime sentAt) {
        this.meetingId = meetingId;
        this.batch = batch;
        this.payload = payload;
        this.sentAt = sentAt;
    }
}
