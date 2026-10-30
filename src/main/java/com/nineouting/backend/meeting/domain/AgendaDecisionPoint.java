package com.nineouting.backend.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 안건의 결정할 사항 한 개 (REQ-066, 124). 안건 보드가 id로 가리킨다 */
@Getter
@Entity
@Table(name = "agenda_decision_points")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgendaDecisionPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String content;

    public AgendaDecisionPoint(int position, String content) {
        this.position = position;
        this.content = content;
    }
}
