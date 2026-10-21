package com.nineouting.backend.meeting.domain;

/** 안건별 논의 현황 (REQ-086). 1차에서는 NOT_STARTED로만 만들고, 바꾸는 것은 안건 보드(2차)에서 한다. */
public enum AgendaStatus {
    NOT_STARTED,
    DISCUSSING,
    DECIDED,
    UNDECIDED
}
