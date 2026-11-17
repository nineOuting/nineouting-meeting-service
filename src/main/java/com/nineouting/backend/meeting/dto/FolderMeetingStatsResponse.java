package com.nineouting.backend.meeting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * REQ-200 프로젝트 회의 현황: 최근 끝난 회의(최대 6개, 오래된 회의부터)의 안건 결과와 시간.
 *
 * @param meetings 회의가 없으면 빈 목록 (화면은 회의 1개 이상부터 그래프를 보인다)
 */
public record FolderMeetingStatsResponse(List<MeetingStat> meetings) {

    /**
     * @param decided          결정된 안건 수
     * @param carried          다음 회의로 넘긴 안건 수
     * @param excluded         결정도 넘김도 아닌 안건 수 (제외)
     * @param scheduledMinutes 예정 시간 (시작~끝 예정 시각)
     * @param actualMinutes    실제 진행 시간 (시작~마무리, 일시정지한 시간은 뺀다)
     */
    public record MeetingStat(Long meetingId, String title, LocalDate date, int decided, int carried, int excluded,
                              long scheduledMinutes, long actualMinutes) {
    }
}
