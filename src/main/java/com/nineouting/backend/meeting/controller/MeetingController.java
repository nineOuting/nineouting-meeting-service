package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.CalendarMeetingResponse;
import com.nineouting.backend.meeting.dto.MeetingDetailResponse;
import com.nineouting.backend.meeting.dto.MeetingPageResponse;
import com.nineouting.backend.meeting.dto.MeetingRequest;
import com.nineouting.backend.meeting.dto.MoveFolderRequest;
import com.nineouting.backend.meeting.service.MeetingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeetingController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final MeetingService meetingService;

    /** REQ-047, 056~067 */
    @PostMapping("/api/folders/{folderId}/meetings")
    public ResponseEntity<ApiResponse<MeetingDetailResponse>> create(@CurrentUser AuthenticatedUser user,
                                                                     @PathVariable Long folderId,
                                                                     @Valid @RequestBody MeetingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(meetingService.create(folderId, user.userId(), request)));
    }

    /** REQ-046 */
    @GetMapping("/api/folders/{folderId}/meetings")
    public ApiResponse<MeetingPageResponse> list(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(meetingService.list(folderId, user.userId(), page, size));
    }

    /** REQ-018~020. 경로가 더 구체적이라 아래 {meetingId}보다 먼저 맞는다. */
    @GetMapping("/api/meetings/calendar")
    public ApiResponse<List<CalendarMeetingResponse>> calendar(@CurrentUser AuthenticatedUser user,
                                                               @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ApiResponse.ok(meetingService.calendar(user.userId(), from, to));
    }

    /** REQ-060, 068, 069 */
    @GetMapping("/api/meetings/{meetingId}")
    public ApiResponse<MeetingDetailResponse> get(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        return ApiResponse.ok(meetingService.get(meetingId, user.userId()));
    }

    /** REQ-116 */
    @PutMapping("/api/meetings/{meetingId}")
    public ApiResponse<MeetingDetailResponse> update(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                                     @Valid @RequestBody MeetingRequest request) {
        return ApiResponse.ok(meetingService.update(meetingId, user.userId(), request));
    }

    /** REQ-113 */
    @PostMapping("/api/meetings/{meetingId}/cancel")
    public ApiResponse<Void> cancel(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        meetingService.cancel(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-043 */
    @PutMapping("/api/meetings/{meetingId}/folder")
    public ApiResponse<Void> move(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                  @Valid @RequestBody MoveFolderRequest request) {
        meetingService.move(meetingId, user.userId(), request.folderId());
        return ApiResponse.ok();
    }
}
