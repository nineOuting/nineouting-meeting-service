package com.nineouting.backend.meeting.dto;

import java.util.List;

/**
 * @param page 0부터 시작
 */
public record MeetingPageResponse(List<MeetingSummaryResponse> items, int page, int size, long totalCount,
                                  boolean hasNext) {
}
