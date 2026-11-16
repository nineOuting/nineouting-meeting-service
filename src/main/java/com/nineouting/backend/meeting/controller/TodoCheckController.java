package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.TodoCheckRequest;
import com.nineouting.backend.meeting.service.TodoCheckService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회의 중 할 일 완료 체크 (디자인 Version02 Live 할 일 표) */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings/{meetingId}/todo-checks")
public class TodoCheckController {

    private final TodoCheckService todoCheckService;

    /** 완료로 체크한 할 일 항목 번호 */
    @GetMapping
    public ApiResponse<List<String>> list(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        return ApiResponse.ok(todoCheckService.list(meetingId, user.userId()));
    }

    /** 회의록 작성자, 진행 중. 바뀐 뒤의 전체 목록을 돌려준다 */
    @PutMapping("/{itemId}")
    public ApiResponse<List<String>> check(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                           @PathVariable @Size(max = 100) String itemId,
                                           @Valid @RequestBody TodoCheckRequest request) {
        return ApiResponse.ok(todoCheckService.check(meetingId, user.userId(), itemId, request.done()));
    }
}
