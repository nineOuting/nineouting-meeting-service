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
 * REQ-185, 186 챗봇 대화 한 개 (질문 또는 답). 사용자마다, 회의마다 따로 본다.
 * 답의 status, basis, citations는 ai-service 응답 그대로다 (회의 중 챗봇 명세 5장). citations는 JSON 문자열.
 */
@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(length = 20)
    private String status;

    @Column(length = 20)
    private String basis;

    @Column(columnDefinition = "TEXT")
    private String citations;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private ChatMessage(Long meetingId, Long userId, Role role, String text, String status, String basis,
                        String citations, LocalDateTime now) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.role = role;
        this.text = text;
        this.status = status;
        this.basis = basis;
        this.citations = citations;
        this.createdAt = now;
    }

    public static ChatMessage question(Long meetingId, Long userId, String text, LocalDateTime now) {
        return new ChatMessage(meetingId, userId, Role.USER, text, null, null, null, now);
    }

    public static ChatMessage answer(Long meetingId, Long userId, String text, String status, String basis,
                                     String citations, LocalDateTime now) {
        return new ChatMessage(meetingId, userId, Role.ASSISTANT, text, status, basis, citations, now);
    }

    /** ai-service history의 role 값 */
    public String wireRole() {
        return role == Role.USER ? "user" : "assistant";
    }
}
