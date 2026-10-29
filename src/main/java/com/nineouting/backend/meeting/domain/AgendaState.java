package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회의 하나의 안건 보드 상태 (REQ-085~097, 124~126, ai-service docs/api/agenda-*.md).
 * ai-service가 보낸 보드는 JSON 그대로 두고, 백엔드가 쓰는 값(버전, 새 안건 제안, 오늘 회의 요약)만 칸으로 둔다.
 */
@Getter
@Entity
@Table(name = "agenda_states")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgendaState {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "agendas_version", nullable = false)
    private long agendasVersion;

    @Column(name = "agendas_dirty", nullable = false)
    private boolean agendasDirty;

    @Column(name = "edit_rev", nullable = false)
    private int editRev;

    @Column(name = "classification_version", nullable = false)
    private long classificationVersion;

    @Column(name = "current_agenda_id")
    private Long currentAgendaId;

    @Column(name = "board_version", nullable = false)
    private long boardVersion;

    @Column(columnDefinition = "TEXT")
    private String board;

    @Column(name = "board_received_at")
    private LocalDateTime boardReceivedAt;

    @Column(name = "off_topic_suggested_at")
    private LocalDateTime offTopicSuggestedAt;

    @Column(name = "off_topic_detected_at", length = 40)
    private String offTopicDetectedAt;

    @Column(name = "today_summary", columnDefinition = "TEXT")
    private String todaySummary;

    public AgendaState(Long meetingId) {
        this.meetingId = meetingId;
    }

    /** 안건이나 참석자가 바뀌어 ai-service에 안건 목록을 다시 보내야 한다 */
    public void markAgendasDirty() {
        this.agendasDirty = true;
    }

    /** 보낼 안건 목록 버전. ai-service는 작거나 같은 버전을 무시하므로 늘 키운다 (재시작해도 작아지지 않게 시각 기준) */
    public long nextAgendasVersion(long nowMillis) {
        this.agendasVersion = Math.max(agendasVersion + 1, nowMillis);
        return agendasVersion;
    }

    public void agendasSent() {
        this.agendasDirty = false;
    }

    /** 고치기·무시·끝내기 요청마다 커지는 rev */
    public int nextRev() {
        return ++editRev;
    }

    /** @return 저장한 것보다 새 분류면 true */
    public boolean applyClassification(long version, Long currentAgendaId) {
        if (version <= classificationVersion) {
            return false;
        }
        this.classificationVersion = version;
        this.currentAgendaId = currentAgendaId;
        return true;
    }

    /**
     * @param suggestDetectedAt 보드의 새 안건 제안이 SUGGEST이면 그 detectedAt, 아니면 null
     * @return 저장한 것보다 새 보드면 true
     */
    public boolean applyBoard(long version, String board, String suggestDetectedAt, LocalDateTime now) {
        if (version <= boardVersion) {
            return false;
        }
        this.boardVersion = version;
        this.board = board;
        this.boardReceivedAt = now;
        if (suggestDetectedAt == null) {
            this.offTopicSuggestedAt = null;
        } else if (!Objects.equals(suggestDetectedAt, offTopicDetectedAt)) {
            // 처음 보는 제안. 30초 안에 등록·무시가 없으면 무시로 보낸다 (REQ-091)
            this.offTopicDetectedAt = suggestDetectedAt;
            this.offTopicSuggestedAt = now;
        }
        return true;
    }

    /** 사람이 무시했거나 30초가 지나 무시로 보냈다. 같은 제안으로 다시 시계를 돌리지 않는다 */
    public void offTopicHandled() {
        this.offTopicSuggestedAt = null;
    }

    /** 회의가 끝난 뒤 마지막 오늘 회의 요약. 점검에서 고친 것이 있으면 덮어쓰지 않는다 */
    public void keepFinalTodaySummary(String todaySummary) {
        if (this.todaySummary == null) {
            this.todaySummary = todaySummary;
        }
    }

    /** 점검(회의가 끝난 뒤)에서 사람이 고친 오늘 회의 요약 */
    public void reviseTodaySummary(String todaySummary) {
        this.todaySummary = todaySummary;
    }
}
