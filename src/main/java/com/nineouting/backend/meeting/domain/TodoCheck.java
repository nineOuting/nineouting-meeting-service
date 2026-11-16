package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회의 중 완료로 체크한 할 일 (안건 보드 할 일 항목 번호). 체크를 풀면 지운다 */
@Getter
@Entity
@Table(name = "todo_checks")
@IdClass(TodoCheck.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoCheck {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Id
    @Column(name = "item_id", length = 100)
    private String itemId;

    @Column(name = "checked_by", nullable = false)
    private Long checkedBy;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    public TodoCheck(Long meetingId, String itemId, Long checkedBy, LocalDateTime checkedAt) {
        this.meetingId = meetingId;
        this.itemId = itemId;
        this.checkedBy = checkedBy;
        this.checkedAt = checkedAt;
    }

    public record Key(Long meetingId, String itemId) implements Serializable {
    }
}
