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
 * ai-service로 보낼 사람의 고치기·무시·끝내기 하나 (ai-service docs/api/agenda-*.md).
 * 같은 대상(kind + itemKey)을 다시 고치면 덮어쓰고 다시 보낸다. payload는 ai-service가 받는 항목 JSON 그대로다(rev 포함).
 * ai-service가 재시작하면 replay인 것을 모두 다시 보낸다 (확인 표시처럼 다시 보낼 필요가 없는 것은 replay=false).
 */
@Getter
@Entity
@Table(name = "agenda_edits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgendaEdit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgendaEditKind kind;

    @Column(name = "item_key", nullable = false, length = 200)
    private String itemKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean replay;

    @Column(nullable = false)
    private boolean sent;

    /** 재시작 복구로 다시 보내는 중 (새 안건 제안 무시는 이때만 atMs를 담는다) */
    @Column(nullable = false)
    private boolean resend;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AgendaEdit(Long meetingId, AgendaEditKind kind, String itemKey, String payload, boolean replay,
                      LocalDateTime now) {
        this.meetingId = meetingId;
        this.kind = kind;
        this.itemKey = itemKey;
        this.payload = payload;
        this.replay = replay;
        this.updatedAt = now;
    }

    public void replace(String payload, LocalDateTime now) {
        this.payload = payload;
        this.sent = false;
        this.resend = false;
        this.updatedAt = now;
    }

    public void markSent() {
        this.sent = true;
        this.resend = false;
    }

    public void markForResend() {
        this.sent = false;
        this.resend = true;
    }
}
