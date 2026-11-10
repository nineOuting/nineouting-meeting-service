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
 * REQ-172 AI가 정리한 내용에 대한 피드백 ([정확해요], [고칠 부분 있어요]). AI 품질을 고치는 데 쓴다.
 * 한 사람이 같은 자리(target)에 하나만 남기고, 다시 누르면 바꾼다.
 */
@Getter
@Entity
@Table(name = "ai_feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFeedback {

    public enum Target {
        /** 회의 중 실시간 요약 */
        LIVE_SUMMARY,
        /** 결정 근거 (안건 현황의 근거 발언) */
        DECISION_EVIDENCE,
        /** 회의록 초안 */
        MINUTES_DRAFT
    }

    public enum Rating {
        ACCURATE,
        NEEDS_FIX
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Target target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rating rating;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AiFeedback(Long meetingId, Long userId, Target target, Rating rating, LocalDateTime now) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.target = target;
        this.rating = rating;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void change(Rating rating, LocalDateTime now) {
        this.rating = rating;
        this.updatedAt = now;
    }
}
