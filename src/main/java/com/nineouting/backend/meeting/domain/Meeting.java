package com.nineouting.backend.meeting.domain;

import com.nineouting.backend.common.web.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회의. 시각은 모두 한국 시간이다.
 * 회의를 만든 사람(ownerUserId)과 회의록 작성자(writerUserId)는 참석자 중 한 명이다.
 */
@Getter
@Entity
@Table(name = "meetings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(length = 100)
    private String location;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "writer_user_id", nullable = false)
    private Long writerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status;

    @Column(name = "previous_meeting_id")
    private Long previousMeetingId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 멈춘 시각 (REQ-084). 멈춰 있지 않으면 null. 회의 상태는 진행 중 그대로다 */
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    /** 끝난 멈춤 시간의 합. 경과 시간(REQ-081)에서 뺀다 */
    @Column(name = "paused_total_ms", nullable = false)
    private long pausedTotalMs;

    /** 회의록 상태 (REQ-048 "검토 중·확정"). minutes-service 이벤트로 바뀐다. 검토를 요청하기 전에는 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "minutes_status", length = 20)
    private MinutesProgress minutesStatus;

    public Meeting(Long workspaceId, Long folderId, Long ownerUserId, Long previousMeetingId) {
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.ownerUserId = ownerUserId;
        this.previousMeetingId = previousMeetingId;
        this.status = MeetingStatus.SCHEDULED;
    }

    /** 생성·수정(REQ-064) 때 바꿀 수 있는 정보 */
    public void changeInfo(String title, LocalDateTime startAt, LocalDateTime endAt, String location, Long writerUserId) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.location = location;
        this.writerUserId = writerUserId;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerUserId.equals(userId);
    }

    public boolean isWriter(Long userId) {
        return writerUserId.equals(userId);
    }

    public boolean is(MeetingStatus status) {
        return this.status == status;
    }

    public void moveTo(Long folderId) {
        this.folderId = folderId;
    }

    public void start(LocalDateTime now) {
        this.status = MeetingStatus.IN_PROGRESS;
        this.startedAt = now;
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    /** REQ-084. 진행 중일 때만 (서비스에서 확인) */
    public void pause(LocalDateTime now) {
        this.pausedAt = now;
    }

    public void resume(LocalDateTime now) {
        this.pausedTotalMs += Duration.between(pausedAt, now).toMillis();
        this.pausedAt = null;
    }

    /** 멈춘 채로 마무리하면 멈춤을 끝내고 마무리한다 */
    public void finish(LocalDateTime now) {
        if (isPaused()) {
            resume(now);
        }
        this.status = MeetingStatus.FINISHED;
        this.finishedAt = now;
    }

    public void cancel(LocalDateTime now) {
        this.status = MeetingStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /** 이벤트 순서가 바뀌어 와도 확정을 검토 중으로 되돌리지 않는다 */
    public void changeMinutesStatus(MinutesProgress minutesStatus) {
        if (this.minutesStatus != MinutesProgress.CONFIRMED) {
            this.minutesStatus = minutesStatus;
        }
    }
}
