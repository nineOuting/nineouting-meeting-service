package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.event.meeting.MeetingFinished;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 회의 시작 → 안건 추가 → 다음 회의로 넘기기 → 마무리, 남은 안건 불러오기 */
class MeetingProgressTest extends ApiTestSupport {

    private Long owner;
    private Long writer;
    private Long folderMember;
    private Long folderId;

    @BeforeEach
    void setUp() {
        owner = newId();
        writer = newId();
        folderMember = newId();
        folderId = folder(newId(), "기획", owner, writer, folderMember);
    }

    @Test
    void 회의록_작성자가_시작하고_안건을_추가하고_마무리한다() throws Exception {
        Long meetingId = createMeeting(folderId, owner, writer, writer);

        call(owner, post("/api/meetings/" + meetingId + "/start"), null)
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        call(writer, post("/api/meetings/" + meetingId + "/agendas"), "{\"title\":\"추가\",\"decisionPoints\":[\"결정\"]}")
                .andExpect(jsonPath("$.code").value("MEETING_NOT_IN_PROGRESS"));
        call(writer, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        call(writer, post("/api/meetings/" + meetingId + "/start"), null)
                .andExpect(jsonPath("$.code").value("MEETING_ALREADY_STARTED"));

        call(owner, post("/api/meetings/" + meetingId + "/agendas"), "{\"title\":\"추가\",\"decisionPoints\":[\"결정\"]}")
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        // 회의 중 안건 추가도 결정할 사항이 1개 이상 있어야 한다
        call(writer, post("/api/meetings/" + meetingId + "/agendas"), "{\"title\":\"추가\",\"decisionPoints\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("decisionPoints"));
        call(writer, post("/api/meetings/" + meetingId + "/agendas"),
                "{\"title\":\"추가\",\"decisionPoints\":[\"결정\",\"담당\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.decisionPoints[*].text", contains("결정", "담당")))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.addedDuringMeeting").value(true));

        call(owner, post("/api/meetings/" + meetingId + "/finish"), null)
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        call(writer, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());
        call(owner, get("/api/meetings/" + meetingId), null)
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.agendas[*].title", contains("안건1", "추가")));
        assertThat(outboxContains(MeetingFinished.TYPE, "\"meetingId\":%d,".formatted(meetingId))).isTrue();
        assertThat(outboxContains(MeetingFinished.TYPE,
                "\"location\":\"3층 회의실\",\"writerUserId\":%d,\"attendeeUserIds\":[%d,%d]".formatted(writer, owner, writer)))
                .isTrue();
    }

    @Test
    void 넘긴_안건은_다음_회의에서_불러오면_목록에서_빠지고_다시_불러올_수_없다() throws Exception {
        Long first = createMeeting(folderId, owner, writer, writer);
        call(writer, post("/api/meetings/" + first + "/start"), null);
        Long agendaId = id(call(writer, get("/api/meetings/" + first), null), "$.data.agendas[0].agendaId");
        call(writer, post("/api/meetings/" + first + "/carry-over"), "{\"agendaIds\":[%d]}".formatted(agendaId))
                .andExpect(status().isOk());
        // 같은 안건을 두 번 넘겨도 남은 안건은 하나다
        call(writer, post("/api/meetings/" + first + "/carry-over"), "{\"agendaIds\":[%d]}".formatted(agendaId));
        call(writer, post("/api/meetings/" + first + "/finish"), null);

        Long pendingId = id(call(folderMember, get("/api/folders/" + folderId + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("안건1"))
                .andExpect(jsonPath("$.data[0].decisionPoints", contains("결정1")))
                .andExpect(jsonPath("$.data[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data[0].fromMeetingTitle").value("회의"))
                .andExpect(jsonPath("$.data[0].carriedAt").isNotEmpty()), "$.data[0].pendingAgendaId");

        String loadPending = "{\"title\":\"안건1\",\"decisionPoints\":[\"결정1\"],\"pendingAgendaId\":%d}".formatted(pendingId);
        Long second = id(call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("두 번째", "2026-10-27T14:00:00", "2026-10-27T15:00:00", writer, List.of(writer), loadPending))
                .andExpect(status().isCreated()), "$.data.meetingId");
        // REQ-052. 다음 회의에 불러오면 남은 안건 목록에서 빠진다
        call(owner, get("/api/folders/" + folderId + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
        call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("세 번째", "2026-10-28T14:00:00", "2026-10-28T15:00:00", owner, List.of(), loadPending))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_AGENDA_UNAVAILABLE"));

        call(writer, post("/api/meetings/" + second + "/start"), null);
        call(writer, post("/api/meetings/" + second + "/finish"), null);
        call(owner, get("/api/folders/" + folderId + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 불러온_안건을_다시_넘기면_같은_줄이_대기로_돌아간다() throws Exception {
        Long pendingId = carriedPendingAgenda();
        String loadPending = "{\"title\":\"안건1\",\"decisionPoints\":[\"결정1\"],\"pendingAgendaId\":%d}".formatted(pendingId);
        Long second = id(call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("두 번째", "2026-10-27T14:00:00", "2026-10-27T15:00:00", writer, List.of(writer), loadPending)),
                "$.data.meetingId");
        call(writer, post("/api/meetings/" + second + "/start"), null);
        Long agendaId = id(call(writer, get("/api/meetings/" + second), null), "$.data.agendas[0].agendaId");

        call(writer, post("/api/meetings/" + second + "/carry-over"), "{\"agendaIds\":[%d]}".formatted(agendaId));
        call(writer, post("/api/meetings/" + second + "/finish"), null);

        call(owner, get("/api/folders/" + folderId + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].pendingAgendaId").value(pendingId))
                .andExpect(jsonPath("$.data[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data[0].fromMeetingTitle").value("두 번째"));
    }

    @Test
    void 회의를_취소하거나_수정에서_안건을_빼면_불러온_안건이_대기로_돌아간다() throws Exception {
        Long pendingId = carriedPendingAgenda();
        String loadPending = "{\"title\":\"안건1\",\"decisionPoints\":[\"결정1\"],\"pendingAgendaId\":%d}".formatted(pendingId);
        String otherAgenda = "{\"title\":\"다른 안건\",\"decisionPoints\":[\"결정\"]}";

        Long edited = id(call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("수정할 회의", "2026-10-27T14:00:00", "2026-10-27T15:00:00", owner, List.of(), loadPending)),
                "$.data.meetingId");
        call(owner, put("/api/meetings/" + edited),
                meetingJson("수정할 회의", "2026-10-27T14:00:00", "2026-10-27T15:00:00", owner, List.of(), otherAgenda))
                .andExpect(status().isOk());
        call(owner, get("/api/folders/" + folderId + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data[0].status").value("WAITING"));

        Long cancelled = id(call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("취소할 회의", "2026-10-28T14:00:00", "2026-10-28T15:00:00", owner, List.of(), loadPending)),
                "$.data.meetingId");
        call(owner, post("/api/meetings/" + cancelled + "/cancel"), null);
        call(owner, get("/api/folders/" + folderId + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data[0].status").value("WAITING"));
    }

    /** 첫 회의에서 안건1을 넘기고 마무리한 뒤 남은 안건 ID */
    private Long carriedPendingAgenda() throws Exception {
        Long first = createMeeting(folderId, owner, writer, writer);
        call(writer, post("/api/meetings/" + first + "/start"), null);
        Long agendaId = id(call(writer, get("/api/meetings/" + first), null), "$.data.agendas[0].agendaId");
        call(writer, post("/api/meetings/" + first + "/carry-over"), "{\"agendaIds\":[%d]}".formatted(agendaId));
        call(writer, post("/api/meetings/" + first + "/finish"), null);
        return id(call(owner, get("/api/folders/" + folderId + "/pending-agendas"), null), "$.data[0].pendingAgendaId");
    }
}
