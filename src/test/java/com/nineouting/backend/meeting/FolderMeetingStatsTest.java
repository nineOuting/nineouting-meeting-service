package com.nineouting.backend.meeting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-200 프로젝트 회의 현황 */
class FolderMeetingStatsTest extends ApiTestSupport {

    @Test
    void 끝난_회의의_안건_결과와_예정_시간을_센다() throws Exception {
        Long owner = newId();
        Long folderId = folder(newId(), "기획", owner);
        String path = "/api/folders/" + folderId + "/meeting-stats";
        call(owner, get(path), null).andExpect(jsonPath("$.data.meetings.length()").value(0));

        String agendas = "{\"title\":\"배포\",\"decisionPoints\":[\"날짜\"]},{\"title\":\"예산\",\"decisionPoints\":[\"금액\"]}";
        Long meetingId = id(call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("주간 회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", owner, List.of(), agendas)),
                "$.data.meetingId");
        // 끝나지 않은 회의는 세지 않는다
        createMeeting(folderId, owner, owner);
        call(owner, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        Long agendaId = id(call(owner, get("/api/meetings/" + meetingId), null), "$.data.agendas[0].agendaId");
        call(owner, post("/api/meetings/" + meetingId + "/carry-over"), "{\"agendaIds\":[%d]}".formatted(agendaId));
        call(owner, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());

        call(owner, get(path), null)
                .andExpect(jsonPath("$.data.meetings.length()").value(1))
                .andExpect(jsonPath("$.data.meetings[0].date").value("2026-10-20"))
                .andExpect(jsonPath("$.data.meetings[0].decided").value(0))
                .andExpect(jsonPath("$.data.meetings[0].carried").value(1))
                .andExpect(jsonPath("$.data.meetings[0].excluded").value(1))
                .andExpect(jsonPath("$.data.meetings[0].scheduledMinutes").value(60));
        call(newId(), get(path), null).andExpect(status().isForbidden());
    }
}
