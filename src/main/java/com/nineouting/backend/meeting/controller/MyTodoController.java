package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.MyTodoResponse;
import com.nineouting.backend.meeting.service.MyTodoService;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** REQ-196 홈 내 할 일 */
@Validated
@RestController
@RequiredArgsConstructor
public class MyTodoController {

    private final MyTodoService myTodoService;

    /** 끝난 회의에서 나에게 배정된, 아직 완료하지 않은 할 일 (최근 회의부터) */
    @GetMapping("/api/meetings/my-todos")
    public ApiResponse<List<MyTodoResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(myTodoService.list(user.userId()));
    }

    /** 내 할 일 완료. 회의 화면의 할 일 체크와 같다 */
    @PostMapping("/api/meetings/{meetingId}/my-todos/{itemId}/done")
    public ApiResponse<Void> complete(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                      @PathVariable @Size(max = 100) String itemId) {
        myTodoService.complete(meetingId, itemId, user.userId());
        return ApiResponse.ok();
    }
}
