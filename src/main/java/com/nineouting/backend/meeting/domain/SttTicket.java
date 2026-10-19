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
 * 음성 연결(브라우저 → stt-service)용 1회용 입장권 (docs/services/ai-integration.md 2.1).
 * 브라우저 WebSocket은 Authorization 헤더를 붙일 수 없어서, 백엔드가 권한을 확인한 뒤 짧게 쓰는 입장권을 준다.
 */
@Getter
@Entity
@Table(name = "stt_tickets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttTicket {

    @Id
    private String token;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public SttTicket(String token, Long meetingId, Long userId, LocalDateTime expiresAt) {
        this.token = token;
        this.meetingId = meetingId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
