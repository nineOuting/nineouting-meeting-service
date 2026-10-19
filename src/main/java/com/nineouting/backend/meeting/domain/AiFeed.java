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
 * 회의 하나를 ai-service로 밀어 주는 상태 (docs/services/ai-integration.md 2.4).
 * 줄 묶음 번호(batch)는 ai-service가 받았다고 답한 뒤에만 올린다. 못 보낸 동안의 변경은 다음 묶음에 함께 담긴다.
 */
@Getter
@Entity
@Table(name = "ai_feeds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFeed {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    /** 다음에 보낼 묶음 번호 (1부터) */
    @Column(name = "next_batch", nullable = false)
    private int nextBatch;

    /** ai-service가 빠졌다고 알려 준 묶음 번호. 여기부터 저장해 둔 묶음을 다시 보낸다 */
    @Column(name = "resend_from")
    private Integer resendFrom;

    /** 연달아 실패한 횟수. 실패 중에는 줄 묶음 주기로만 다시 시도한다 */
    @Column(nullable = false)
    private int failures;

    /** 다음 줄 묶음을 보낼 시각 */
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    public AiFeed(Long meetingId, LocalDateTime now) {
        this.meetingId = meetingId;
        this.nextBatch = 1;
        this.nextAttemptAt = now;
    }

    public boolean isBatchDue(LocalDateTime now) {
        return !nextAttemptAt.isAfter(now);
    }

    public void batchSent() {
        nextBatch++;
    }

    public void requestResend(int expectedBatch) {
        if (expectedBatch < nextBatch) {
            resendFrom = expectedBatch;
        } else {
            // 백엔드가 기억하는 것보다 앞선 번호를 기다리면 그 번호로 맞춘다
            nextBatch = expectedBatch;
        }
    }

    public void resendDone() {
        resendFrom = null;
    }

    public void succeeded(LocalDateTime nextAttemptAt) {
        this.failures = 0;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void failed(LocalDateTime nextAttemptAt) {
        this.failures++;
        this.nextAttemptAt = nextAttemptAt;
    }
}
