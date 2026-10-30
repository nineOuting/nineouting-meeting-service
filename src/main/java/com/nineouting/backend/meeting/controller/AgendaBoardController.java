package com.nineouting.backend.meeting.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.dto.AgendaBoardResponse;
import com.nineouting.backend.meeting.dto.AgendaItemConfirmRequest;
import com.nineouting.backend.meeting.dto.AgendaItemEditRequest;
import com.nineouting.backend.meeting.dto.DecisionEditRequest;
import com.nineouting.backend.meeting.dto.LineAgendaRequest;
import com.nineouting.backend.meeting.dto.LineAgendaResponse;
import com.nineouting.backend.meeting.dto.TodaySummaryResponse;
import com.nineouting.backend.meeting.service.AgendaBoardService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 안건 보드 (REQ-085~097, 124~126). 보기는 참석자, 고치기·무시·끝내기는 회의록 작성자(진행 중).
 * 고친 결과는 ai-service가 반영한 보드로 회의방에 다시 온다 (RoomMessage agendaBoard).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings/{meetingId}")
public class AgendaBoardController {

    private final AgendaBoardService agendaBoardService;

    /** REQ-085~088, 124~125 지금 보드 (회의방에 늦게 들어왔을 때) */
    @GetMapping("/agenda-board")
    public ApiResponse<AgendaBoardResponse> board(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        return ApiResponse.ok(agendaBoardService.board(meetingId, user.userId()));
    }

    /** REQ-089 전사 줄마다 안건 */
    @GetMapping("/line-agendas")
    public ApiResponse<List<LineAgendaResponse>> lineAgendas(@CurrentUser AuthenticatedUser user,
                                                             @PathVariable Long meetingId) {
        return ApiResponse.ok(agendaBoardService.lineAgendas(meetingId, user.userId()));
    }

    /** REQ-089 줄의 안건 고치기 (agendaId null = 해당 없음) */
    @PutMapping("/transcripts/{seq}/agenda")
    public ApiResponse<LineAgendaResponse> correctLine(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                                       @PathVariable int seq, @RequestBody LineAgendaRequest request) {
        return ApiResponse.ok(agendaBoardService.correctLine(meetingId, user.userId(), seq, request.agendaId()));
    }

    /** REQ-085 요약 문장, REQ-087 의견, REQ-097 할 일 고치기. target: summary / opinions / todos */
    @PutMapping("/agenda-board/{target}/{itemId}")
    public ApiResponse<Void> editItem(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                      @PathVariable String target, @PathVariable String itemId,
                                      @Valid @RequestBody AgendaItemEditRequest request) {
        agendaBoardService.editItem(meetingId, user.userId(), target, itemId, request);
        return ApiResponse.ok();
    }

    /** "확인 필요" 표시를 확인함 */
    @PostMapping("/agenda-board/{target}/{itemId}/confirm")
    public ApiResponse<Void> confirmItem(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                         @PathVariable String target, @PathVariable String itemId,
                                         @Valid @RequestBody AgendaItemConfirmRequest request) {
        agendaBoardService.confirmItem(meetingId, user.userId(), target, itemId, request.agendaId());
        return ApiResponse.ok();
    }

    /** REQ-126 결정 내용 고치기 */
    @PutMapping("/agendas/{agendaId}/decision-points/{decisionPointId}/decision")
    public ApiResponse<Void> editDecision(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId,
                                          @PathVariable Long agendaId, @PathVariable Long decisionPointId,
                                          @Valid @RequestBody DecisionEditRequest request) {
        agendaBoardService.editDecision(meetingId, user.userId(), agendaId, decisionPointId, request.decisionText(),
                request.evidenceSeqs());
        return ApiResponse.ok();
    }

    /** REQ-091 새 안건 제안 [무시], REQ-092 주제 이탈 알림 [확인]. 등록은 안건 추가(POST /agendas)로 한다 */
    @PostMapping("/off-topic/dismiss")
    public ApiResponse<Void> dismissOffTopic(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        agendaBoardService.dismissOffTopic(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-093 회의 끝내기 (결론 나지 않은 안건 목록·오늘 회의 요약이 보드로 온다). 녹음은 마무리(REQ-098)까지 계속된다 */
    @PostMapping("/end-request")
    public ApiResponse<Void> requestEnd(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        agendaBoardService.requestEnd(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-095 이어서 논의하기 */
    @DeleteMapping("/end-request")
    public ApiResponse<Void> cancelEnd(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        agendaBoardService.cancelEnd(meetingId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-097 오늘 회의 요약 */
    @GetMapping("/today-summary")
    public ApiResponse<TodaySummaryResponse> todaySummary(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable Long meetingId) {
        return ApiResponse.ok(agendaBoardService.todaySummary(meetingId, user.userId()));
    }

    /** REQ-097 점검: 회의가 끝난 뒤 고친 오늘 회의 요약 저장 ({agendas[...]} 전체) */
    @PutMapping("/today-summary")
    public ApiResponse<TodaySummaryResponse> reviseTodaySummary(@CurrentUser AuthenticatedUser user,
                                                                @PathVariable Long meetingId,
                                                                @RequestBody JsonNode todaySummary) {
        return ApiResponse.ok(agendaBoardService.reviseTodaySummary(meetingId, user.userId(), todaySummary));
    }
}
