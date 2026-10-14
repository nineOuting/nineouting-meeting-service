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

/** 회의 참석자. 회의를 만든 사람도 들어간다. */
@Getter
@Entity
@Table(name = "meeting_attendees")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public MeetingAttendee(Long meetingId, Long userId) {
        this.meetingId = meetingId;
        this.userId = userId;
    }
}
