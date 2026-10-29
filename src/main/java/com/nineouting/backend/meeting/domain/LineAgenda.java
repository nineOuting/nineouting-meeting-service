package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전사 줄이 어느 안건인지 (REQ-089). ai-service가 분류하고, 회의록 작성자가 고치면 corrected가 되어
 * 그 뒤 자동 분류가 덮어쓰지 않는다 (ai-service가 고친 줄은 보내지 않는다). agendaId가 null이면 해당 없음.
 */
@Getter
@Entity
@Table(name = "line_agendas")
@IdClass(LineAgenda.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineAgenda {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Id
    private int seq;

    @Column(name = "agenda_id")
    private Long agendaId;

    @Column(nullable = false)
    private boolean corrected;

    public LineAgenda(Long meetingId, int seq) {
        this.meetingId = meetingId;
        this.seq = seq;
    }

    /** ai-service 분류. 고친 줄이 와도 받는다 (안건이 지워져 해당 없음이 된 경우만 온다) */
    public void classify(Long agendaId) {
        this.agendaId = agendaId;
    }

    public void correct(Long agendaId) {
        this.agendaId = agendaId;
        this.corrected = true;
    }

    @Getter
    @NoArgsConstructor
    @lombok.EqualsAndHashCode
    public static class Key implements Serializable {

        private Long meetingId;
        private int seq;

        public Key(Long meetingId, int seq) {
            this.meetingId = meetingId;
            this.seq = seq;
        }
    }
}
