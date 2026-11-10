package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.AiFeedbackRequest;
import com.nineouting.backend.meeting.dto.TrashMeetingResponse;
import com.nineouting.backend.meeting.service.AiFeedbackService;
import com.nineouting.backend.meeting.service.MeetingTrashService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회의 삭제와 쓰레기통 (REQ-166, REQ-143), AI 결과 피드백 (REQ-172) */
@RestController
@RequiredArgsConstructor
public class MeetingTrashController {

    private final MeetingTrashService trashService;
    private final AiFeedbackService aiFeedbackService;

    /** REQ-166 회의 삭제: 쓰레기통으로. 회의를 만든 사람만, 진행 중이면 안 된다 */
    @DeleteMapping("/api/meetings/{meetingId}")
    public ApiResponse<Void> trash(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        trashService.trash(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-143 쓰레기통의 회의 (내가 지웠거나 내가 만든 회의) */
    @GetMapping("/api/meetings/trash")
    public ApiResponse<List<TrashMeetingResponse>> list(@CurrentUser AuthenticatedUser user,
                                                        @RequestParam Long workspaceId) {
        return ApiResponse.ok(trashService.list(workspaceId, user.userId()));
    }

    /** REQ-143 복구, REQ-166 [되돌리기] */
    @PostMapping("/api/meetings/{meetingId}/restore")
    public ApiResponse<Void> restore(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        trashService.restore(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-143 영구 삭제 (쓰레기통에 있는 회의만) */
    @DeleteMapping("/api/meetings/{meetingId}/purge")
    public ApiResponse<Void> purge(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        trashService.purge(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-172 [정확해요], [고칠 부분 있어요] */
    @PutMapping("/api/meetings/{meetingId}/ai-feedback")
    public ApiResponse<Void> feedback(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                      @Valid @RequestBody AiFeedbackRequest request) {
        aiFeedbackService.save(meetingId, user.userId(), request.target(), request.rating());
        return ApiResponse.ok();
    }
}
