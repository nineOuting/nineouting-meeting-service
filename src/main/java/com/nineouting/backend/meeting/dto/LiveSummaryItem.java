package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 요약 한 줄. evidence는 근거가 된 전사 구간 (음성 시각, 초 단위 실수).
 * 화면은 전사 줄의 startMs와 맞춰 근거로 이동할 수 있다.
 */
public record LiveSummaryItem(@NotNull @Size(max = 2000) String text, String topic, List<Evidence> evidence) {

    public record Evidence(double start, double end) {
    }
}
