package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회의 중 요약 (ai-service가 보낸 가장 최신 하나). 저장은 저장소의 upsert로만 한다 (version이 클 때만).
 * summary는 요약 항목 배열 JSON 그대로다.
 */
@Getter
@Entity
@Table(name = "live_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveSummary {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(nullable = false)
    private long version;

    @Column(name = "at_ms", nullable = false)
    private long atMs;

    @Column(name = "is_final", nullable = false)
    private boolean finalSummary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
