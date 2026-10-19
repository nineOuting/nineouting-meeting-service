package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전사 텍스트 한 줄 (REQ-072, 112, docs/services/ai-integration.md 2.2).
 * stt-service가 보낸 줄은 저장소의 upsert로 들어오고(rev가 더 클 때만, 확정된 줄은 그대로),
 * 확정된 뒤의 변경은 모두 회의록 작성자의 수정·삭제다. 수정·삭제할 때마다 rev를 1씩 이어서 올린다.
 */
@Getter
@Entity
@Table(name = "transcript_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranscriptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private int rev;

    @Column(name = "speaker_label", length = 50)
    private String speakerLabel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "is_final", nullable = false)
    private boolean finalLine;

    @Column(name = "low_confidence", nullable = false)
    private boolean lowConfidence;

    /** backchannel(맞장구) / filler(간투사) / null(일반 발언) */
    @Column(length = 20)
    private String kind;

    /** 놓친 말을 다시 인식해 앞 시각에 끼워 넣은 줄 */
    @Column(nullable = false)
    private boolean recovered;

    @Column(nullable = false)
    private boolean deleted;

    /** ai-service에 마지막으로 넘긴 rev. 아직 넘기지 않았으면 null */
    @Column(name = "sent_rev")
    private Integer sentRev;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 확정되고 지우지 않은 줄만 고칠 수 있다 (중간 결과는 STT가 계속 바꾼다) */
    public boolean isEditable() {
        return finalLine && !deleted;
    }

    /** 작성자가 고친 줄은 더 이상 불확실하지 않다. kind는 ai-service가 다시 판별한다 */
    public void editText(String text, LocalDateTime now) {
        this.text = text;
        this.lowConfidence = false;
        this.rev++;
        this.updatedAt = now;
    }

    public void delete(LocalDateTime now) {
        this.deleted = true;
        this.rev++;
        this.updatedAt = now;
    }

    /** ai-service로 넘길 새 줄: 확정됐고, 아직 넘기지 않았고, 지우지 않은 줄 */
    public boolean isNewForAi() {
        return finalLine && sentRev == null && !deleted;
    }

    /** ai-service로 넘길 변경: 이미 넘긴 줄이 그 뒤에 고쳐지거나 지워짐 */
    public boolean isChangedForAi() {
        return sentRev != null && rev > sentRev;
    }

    public void markSentToAi() {
        this.sentRev = rev;
    }
}
