package com.nineouting.backend.meeting.dto;

import java.util.List;

/** 안건의 결정할 사항 제한 (2026-10-23 결정: 안건당 최대 5개, 칸당 200자. 기획 확정 전 임시 값) */
final class DecisionPoints {

    static final int MAX_COUNT = 5;
    static final int MAX_LENGTH = 200;

    private DecisionPoints() {
    }

    /** 칸마다 앞뒤 공백을 지운다. 빈 칸은 검증에서 걸러지도록 그대로 둔다 */
    static List<String> trim(List<String> values) {
        return values == null ? null : values.stream().map(value -> value == null ? null : value.trim()).toList();
    }
}
