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
 * 폴더의 남은 안건 (REQ-051). 같은 안건을 여러 번 넘겨도 한 줄로 유지하고 마지막으로 넘긴 회의·날짜를 기록한다.
 */
@Getter
@Entity
@Table(name = "pending_agendas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "decision_point", nullable = false, length = 500)
    private String decisionPoint;

    @Column(name = "from_meeting_id", nullable = false)
    private Long fromMeetingId;

    @Column(name = "carried_at", nullable = false)
    private LocalDateTime carriedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingAgendaStatus status;

    @Column(name = "taken_meeting_id")
    private Long takenMeetingId;

    public PendingAgenda(Long workspaceId, Long folderId, String title, String decisionPoint, Long fromMeetingId,
                         LocalDateTime carriedAt) {
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.title = title;
        this.decisionPoint = decisionPoint;
        this.fromMeetingId = fromMeetingId;
        this.carriedAt = carriedAt;
        this.status = PendingAgendaStatus.WAITING;
    }

    /** 다음 회의에 불러옴 (REQ-052) */
    public void takeBy(Long meetingId) {
        this.status = PendingAgendaStatus.IN_PROGRESS;
        this.takenMeetingId = meetingId;
    }

    public boolean isWaiting() {
        return status == PendingAgendaStatus.WAITING;
    }

    public boolean isTakenBy(Long meetingId) {
        return status == PendingAgendaStatus.IN_PROGRESS && meetingId.equals(takenMeetingId);
    }

    /** 불러온 회의가 취소됐거나 시작 전에 안건을 뺐다 */
    public void release() {
        this.status = PendingAgendaStatus.WAITING;
        this.takenMeetingId = null;
    }

    /** 불러온 회의에서 다시 다음 회의로 넘겼다 (REQ-094) */
    public void carryAgain(Long fromMeetingId, LocalDateTime carriedAt) {
        release();
        this.fromMeetingId = fromMeetingId;
        this.carriedAt = carriedAt;
    }

    public void done() {
        this.status = PendingAgendaStatus.DONE;
    }
}
