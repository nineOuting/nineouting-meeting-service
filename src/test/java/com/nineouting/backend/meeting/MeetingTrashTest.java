package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.meeting.repository.AiFeedbackRepository;
import com.nineouting.backend.meeting.service.MeetingTrashService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** REQ-166 회의 삭제, REQ-143 쓰레기통, REQ-172 AI 피드백, REQ-173 회의 중 안건 고치기와 삭제 */
class MeetingTrashTest extends ApiTestSupport {

    @Autowired
    AiFeedbackRepository aiFeedbackRepository;

    @Autowired
    MeetingTrashService trashService;

    @Test
    void 만든_사람이_회의를_지우면_목록과_상세에서_사라지고_복구하면_돌아온다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long attendee = newId();
        Long folderId = folder(workspaceId, "폴더", owner, attendee);
        Long meetingId = createMeeting(folderId, owner, owner, attendee);

        call(attendee, delete("/api/meetings/" + meetingId), null).andExpect(status().isForbidden());
        call(owner, delete("/api/meetings/" + meetingId), null).andExpect(status().isOk());
        assertThat(outboxContains("MeetingTrashed", "\"meetingId\":" + meetingId)).isTrue();

        call(owner, get("/api/meetings/" + meetingId), null).andExpect(status().isNotFound());
        call(owner, get("/api/folders/" + folderId + "/meetings"), null)
                .andExpect(jsonPath("$.data.items.length()").value(0));
        call(owner, get("/api/meetings/trash").param("workspaceId", workspaceId.toString()), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].meetingId").value(meetingId))
                .andExpect(jsonPath("$.data[0].folderName").value("폴더"))
                .andExpect(jsonPath("$.data[0].deletedBy.userId").value(owner))
                .andExpect(jsonPath("$.data[0].hasMinutes").value(false))
                .andExpect(jsonPath("$.data[0].purgeAt").isNotEmpty());
        // 지우지도 만들지도 않은 사람의 쓰레기통에는 없고, 복구할 수도 없다
        call(attendee, get("/api/meetings/trash").param("workspaceId", workspaceId.toString()), null)
                .andExpect(jsonPath("$.data.length()").value(0));
        call(attendee, post("/api/meetings/" + meetingId + "/restore"), null).andExpect(status().isForbidden());

        call(owner, post("/api/meetings/" + meetingId + "/restore"), null).andExpect(status().isOk());
        assertThat(outboxContains("MeetingRestored", "\"meetingId\":" + meetingId)).isTrue();
        call(attendee, get("/api/meetings/" + meetingId), null).andExpect(status().isOk());
        call(owner, post("/api/meetings/" + meetingId + "/restore"), null).andExpect(status().isNotFound());
    }

    @Test
    void 진행_중인_회의는_지울_수_없고_영구_삭제하면_되돌릴_수_없다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        call(owner, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());

        call(owner, delete("/api/meetings/" + meetingId), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_IN_PROGRESS_UNDELETABLE"));
        call(owner, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());
        call(owner, delete("/api/meetings/" + meetingId), null).andExpect(status().isOk());
        call(owner, get("/api/meetings/trash").param("workspaceId", workspaceId.toString()), null)
                .andExpect(jsonPath("$.data[0].hasMinutes").value(true));

        call(owner, delete("/api/meetings/" + meetingId + "/purge"), null).andExpect(status().isOk());
        assertThat(outboxContains("MeetingPurged", "\"meetingId\":" + meetingId)).isTrue();
        call(owner, post("/api/meetings/" + meetingId + "/restore"), null).andExpect(status().isNotFound());
        call(owner, get("/api/meetings/trash").param("workspaceId", workspaceId.toString()), null)
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 보관_기간이_지난_회의는_자동으로_영구_삭제된다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        call(owner, delete("/api/meetings/" + meetingId), null).andExpect(status().isOk());

        // 아직 보관 기간 안이라 남는다
        trashService.purgeExpired();
        call(owner, get("/api/meetings/trash").param("workspaceId", workspaceId.toString()), null)
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void AI_피드백은_한_사람이_같은_자리에_하나만_남긴다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long outsider = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        String path = "/api/meetings/" + meetingId + "/ai-feedback";

        call(outsider, put(path), "{\"target\":\"LIVE_SUMMARY\",\"rating\":\"ACCURATE\"}")
                .andExpect(status().isForbidden());
        call(owner, put(path), "{\"target\":\"LIVE_SUMMARY\"}").andExpect(status().isBadRequest());
        call(owner, put(path), "{\"target\":\"LIVE_SUMMARY\",\"rating\":\"ACCURATE\"}").andExpect(status().isOk());
        call(owner, put(path), "{\"target\":\"LIVE_SUMMARY\",\"rating\":\"NEEDS_FIX\"}").andExpect(status().isOk());

        assertThat(aiFeedbackRepository.findAll())
                .filteredOn(feedback -> feedback.getMeetingId().equals(meetingId))
                .singleElement()
                .satisfies(feedback -> assertThat(feedback.getRating().name()).isEqualTo("NEEDS_FIX"));
    }

    @Test
    void 회의록_작성자는_회의_중에_안건_이름을_고치고_지운다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long writer = newId();
        Long folderId = folder(workspaceId, "폴더", owner, writer);
        Long meetingId = createMeeting(folderId, owner, writer, writer);
        call(writer, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        Long firstAgenda = id(call(writer, get("/api/meetings/" + meetingId), null), "$.data.agendas[0].agendaId");
        Long secondAgenda = id(call(writer, post("/api/meetings/" + meetingId + "/agendas"),
                "{\"title\":\"새 안건\",\"decisionPoints\":[\"결정\"]}").andExpect(status().isCreated()), "$.data.agendaId");
        String first = "/api/meetings/" + meetingId + "/agendas/" + firstAgenda;

        call(owner, put(first), "{\"title\":\"바꾼 안건\"}").andExpect(status().isForbidden());
        call(writer, put(first), "{\"title\":\" \"}").andExpect(status().isBadRequest());
        call(writer, put(first), "{\"title\":\"바꾼 안건\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바꾼 안건"));

        call(writer, delete(first), null).andExpect(status().isOk());
        call(writer, get("/api/meetings/" + meetingId), null)
                .andExpect(jsonPath("$.data.agendas.length()").value(1))
                .andExpect(jsonPath("$.data.agendas[0].agendaId").value(secondAgenda));
        call(writer, delete("/api/meetings/" + meetingId + "/agendas/" + secondAgenda), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_AGENDA_UNDELETABLE"));
    }
}
