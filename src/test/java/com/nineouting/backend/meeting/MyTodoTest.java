package com.nineouting.backend.meeting;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.meeting.domain.AgendaState;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** REQ-196 홈 내 할 일 */
class MyTodoTest extends ApiTestSupport {

    @Autowired
    AgendaStateRepository agendaStateRepository;

    @Test
    void 끝난_회의에서_나에게_배정된_할_일을_보여주고_완료하면_빠진다() throws Exception {
        Long owner = newId();
        Long me = newId();
        Long other = newId();
        Long folderId = folder(newId(), "기획", owner, me, other);
        Long meetingId = createMeeting(folderId, owner, owner, me, other);
        call(owner, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        saveBoard(meetingId, """
                {"version":1,"agendas":[{"agendaId":1,"todos":[
                  {"id":"t-1","task":"QA 일정표 공유","assigneeUserId":%d,"dueText":"수요일까지"},
                  {"id":"t-2","task":"남의 일","assigneeUserId":%d,"dueText":null},
                  {"id":"t-3","task":"담당자 없음","assigneeUserId":null}]}]}""".formatted(me, other));

        // 진행 중인 회의의 할 일은 아직 보이지 않는다
        call(me, get("/api/meetings/my-todos"), null).andExpect(jsonPath("$.data.length()").value(0));
        call(owner, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());

        call(me, get("/api/meetings/my-todos"), null)
                .andExpect(jsonPath("$.data[*].itemId", contains("t-1")))
                .andExpect(jsonPath("$.data[0].task").value("QA 일정표 공유"))
                .andExpect(jsonPath("$.data[0].dueText").value("수요일까지"))
                .andExpect(jsonPath("$.data[0].folderName").value("기획"));

        call(me, post("/api/meetings/" + meetingId + "/my-todos/t-2/done"), null).andExpect(status().isNotFound());
        call(newId(), post("/api/meetings/" + meetingId + "/my-todos/t-1/done"), null).andExpect(status().isForbidden());
        call(me, post("/api/meetings/" + meetingId + "/my-todos/t-1/done"), null).andExpect(status().isOk());

        call(me, get("/api/meetings/my-todos"), null).andExpect(jsonPath("$.data.length()").value(0));
        // 회의 화면의 할 일 체크와 같은 기록이다
        call(owner, get("/api/meetings/" + meetingId + "/todo-checks"), null).andExpect(jsonPath("$.data", contains("t-1")));
        call(other, get("/api/meetings/my-todos"), null).andExpect(jsonPath("$.data[*].itemId", contains("t-2")));
    }

    private void saveBoard(Long meetingId, String board) {
        AgendaState state = agendaStateRepository.findById(meetingId).orElseGet(() -> new AgendaState(meetingId));
        state.applyBoard(1, board, null, LocalDateTime.now());
        agendaStateRepository.save(state);
    }
}
