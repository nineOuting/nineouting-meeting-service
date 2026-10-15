package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.AddAgendaRequest;
import com.nineouting.backend.meeting.dto.AgendaResponse;
import com.nineouting.backend.meeting.dto.CarryOverRequest;
import com.nineouting.backend.meeting.dto.PendingAgendaResponse;
import com.nineouting.backend.meeting.service.MeetingProgressService;
import com.nineouting.backend.meeting.service.PendingAgendaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 회의 진행(1차: 상태만)과 폴더의 남은 안건 */
@RestController
@RequiredArgsConstructor
public class MeetingProgressController {

    private final MeetingProgressService progressService;
    private final PendingAgendaService pendingAgendaService;

    /** REQ-070 */
    @PostMapping("/api/meetings/{meetingId}/start")
    public ApiResponse<Void> start(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        progressService.start(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-114 */
    @PostMapping("/api/meetings/{meetingId}/agendas")
    public ResponseEntity<ApiResponse<AgendaResponse>> addAgenda(@CurrentUser AuthenticatedUser user,
                                                                 @PathVariable Long meetingId,
                                                                 @Valid @RequestBody AddAgendaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(progressService.addAgenda(meetingId, user.userId(), request)));
    }

    /** REQ-083 */
    @PostMapping("/api/meetings/{meetingId}/carry-over")
    public ApiResponse<Void> carryOver(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                       @Valid @RequestBody CarryOverRequest request) {
        progressService.carryOver(meetingId, user.userId(), request.agendaIds());
        return ApiResponse.ok();
    }

    /** REQ-087 */
    @PostMapping("/api/meetings/{meetingId}/finish")
    public ApiResponse<Void> finish(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        progressService.finish(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-049, 050 */
    @GetMapping("/api/folders/{folderId}/pending-agendas")
    public ApiResponse<List<PendingAgendaResponse>> pendingAgendas(@CurrentUser AuthenticatedUser user,
                                                                   @PathVariable Long folderId) {
        return ApiResponse.ok(pendingAgendaService.list(folderId, user.userId()));
    }
}
