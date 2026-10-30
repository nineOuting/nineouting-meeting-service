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

/**
 * 회의 진행 알림 (시작·멈춤·재개·종료, docs/services/ai-integration.md 2.3). stt-service가 보낸다.
 * 회의 상태는 바꾸지 않고 기록해 두었다가 ai-service로 넘긴다.
 * atMs는 STT 음성 시간축(전사 줄 startMs와 같은 기준), receivedAt은 백엔드가 받은 서버 시각이다.
 */
@Getter
@Entity
@Table(name = "progress_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgressEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProgressEventType event;

    @Column(name = "at_ms", nullable = false)
    private long atMs;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** ai-service로 넘겼는지 */
    @Column(nullable = false)
    private boolean sent;

    public void markSent() {
        this.sent = true;
    }
}
