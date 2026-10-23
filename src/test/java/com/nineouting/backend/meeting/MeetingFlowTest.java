package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.event.meeting.MeetingCancelled;
import com.nineouting.backend.common.event.meeting.MeetingCreated;
import com.nineouting.backend.common.event.meeting.MeetingMoved;
import com.nineouting.backend.common.event.meeting.MeetingUpdated;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 회의 생성·조회·수정·취소·폴더 이동, 폴더 내 목록, 홈 캘린더 */
class MeetingFlowTest extends ApiTestSupport {

    private Long workspaceId;
    private Long owner;
    private Long member;
    private Long outsider;
    private Long folderId;

    @BeforeEach
    void setUp() {
        workspaceId = newId();
        owner = newId();
        member = newId();
        outsider = newId();
        folderId = folder(workspaceId, "기획", owner, member);
    }

    @Test
    void 회의를_만들면_만든_사람도_참석자가_되고_참석자에게_알림_이벤트가_간다() throws Exception {
        String body = meetingJson("주간 회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", member, List.of(member),
                "{\"title\":\"배포 일정\",\"decisionPoints\":[\"배포 날짜 확정\"]}",
                "{\"title\":\"예산\",\"decisionPoints\":[\"예산 승인\",\"  담당자 지정 \"]}");

        Long meetingId = id(call(owner, post("/api/folders/" + folderId + "/meetings"), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.startAt").value("2026-10-20T14:00:00"))
                .andExpect(jsonPath("$.data.owner.name").value("user" + owner))
                .andExpect(jsonPath("$.data.writer.userId").value(member))
                .andExpect(jsonPath("$.data.attendees[*].userId", contains(owner.intValue(), member.intValue())))
                .andExpect(jsonPath("$.data.agendas[*].title", contains("배포 일정", "예산"))), "$.data.meetingId");

        call(member, get("/api/meetings/" + meetingId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.location").value("3층 회의실"))
                .andExpect(jsonPath("$.data.agendas[1].decisionPoints[*].text", contains("예산 승인", "담당자 지정")))
                .andExpect(jsonPath("$.data.agendas[1].decisionPoints[*].position", contains(0, 1)))
                .andExpect(jsonPath("$.data.agendas[1].decisionPoints[0].decisionPointId").isNumber());
        call(outsider, get("/api/meetings/" + meetingId), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FOLDER_ACCESS_DENIED"));
        assertThat(outboxContains(MeetingCreated.TYPE, "\"attendeeUserIds\":[%d,%d]".formatted(owner, member))).isTrue();
    }

    @Test
    void 필수_항목이_비면_칸별로_안내한다() throws Exception {
        String body = """
                {"title":" ","startAt":null,"endAt":"2026-10-20T15:00:00","writerUserId":%d,
                 "agendas":[{"title":"안건","decisionPoints":[""]}]}""".formatted(owner);

        call(owner, post("/api/folders/" + folderId + "/meetings"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data[*].field", containsInAnyOrder("title", "startAt", "agendas[0].decisionPoints[0]")));
        call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", owner, List.of()))
                .andExpect(jsonPath("$.data[0].field").value("agendas"));
    }

    @Test
    void 결정할_사항은_안건마다_1개에서_5개까지_칸당_200자까지다() throws Exception {
        String none = "{\"title\":\"안건\",\"decisionPoints\":[]}";
        String six = "{\"title\":\"안건\",\"decisionPoints\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}";
        String tooLong = "{\"title\":\"안건\",\"decisionPoints\":[\"%s\"]}".formatted("가".repeat(201));
        for (String agenda : List.of(none, six, tooLong)) {
            call(owner, post("/api/folders/" + folderId + "/meetings"),
                    meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", owner, List.of(), agenda))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        String five = "{\"title\":\"안건\",\"decisionPoints\":[\"1\",\"2\",\"3\",\"4\",\"%s\"]}"
                .formatted("가".repeat(200));
        call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", owner, List.of(), five))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.agendas[0].decisionPoints.length()").value(5));
    }

    @Test
    void 시간_참석자_회의록_작성자를_확인한다() throws Exception {
        String agenda = "{\"title\":\"안건\",\"decisionPoints\":[\"결정\"]}";
        String url = "/api/folders/" + folderId + "/meetings";

        call(owner, post(url), meetingJson("회의", "2026-10-20T15:00:00", "2026-10-20T14:00:00", owner, List.of(), agenda))
                .andExpect(jsonPath("$.code").value("MEETING_TIME_INVALID"));
        call(owner, post(url), meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", owner, List.of(outsider), agenda))
                .andExpect(jsonPath("$.code").value("ATTENDEE_NOT_FOLDER_MEMBER"));
        call(owner, post(url), meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", member, List.of(), agenda))
                .andExpect(jsonPath("$.code").value("WRITER_NOT_ATTENDEE"));
        call(outsider, post(url), meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", outsider, List.of(), agenda))
                .andExpect(status().isForbidden());
        call(owner, post("/api/folders/" + newId() + "/meetings"),
                meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", owner, List.of(), agenda))
                .andExpect(status().isNotFound());
    }

    @Test
    void 참석자는_시작_전에_수정할_수_있고_추가된_사람과_빠진_사람을_알린다() throws Exception {
        Long third = newId();
        Long folder = folder(workspaceId, "개발", owner, member, third);
        Long meetingId = createMeeting(folder, owner, owner, member);
        String changed = meetingJson("바뀐 회의", "2026-10-21T10:00:00", "2026-10-21T11:00:00", third, List.of(third),
                "{\"title\":\"새 안건\",\"decisionPoints\":[\"새 결정\"]}");

        call(third, put("/api/meetings/" + meetingId), changed)
                .andExpect(jsonPath("$.code").value("NOT_MEETING_ATTENDEE"));
        call(member, put("/api/meetings/" + meetingId), changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바뀐 회의"))
                .andExpect(jsonPath("$.data.attendees[*].userId", containsInAnyOrder(owner.intValue(), third.intValue())))
                .andExpect(jsonPath("$.data.agendas.length()").value(1))
                .andExpect(jsonPath("$.data.agendas[0].title").value("새 안건"));
        assertThat(outboxContains(MeetingUpdated.TYPE,
                "\"addedUserIds\":[%d],\"removedUserIds\":[%d]".formatted(third, member))).isTrue();

        call(third, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        call(owner, put("/api/meetings/" + meetingId), changed)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_EDITABLE"));
    }

    @Test
    void 취소한_회의는_목록과_캘린더에서_숨기고_참석자에게_알린다() throws Exception {
        Long meetingId = createMeeting(folderId, owner, owner, member);

        call(outsider, post("/api/meetings/" + meetingId + "/cancel"), null)
                .andExpect(status().isForbidden());
        call(member, post("/api/meetings/" + meetingId + "/cancel"), null).andExpect(status().isOk());

        call(member, get("/api/meetings/" + meetingId), null).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        call(member, get("/api/folders/" + folderId + "/meetings"), null)
                .andExpect(jsonPath("$.data.totalCount").value(0));
        call(member, get("/api/meetings/calendar").param("from", "2026-10-01").param("to", "2026-10-31"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(outboxContains(MeetingCancelled.TYPE, "\"meetingId\":" + meetingId)).isTrue();
        call(owner, post("/api/meetings/" + meetingId + "/cancel"), null)
                .andExpect(jsonPath("$.code").value("MEETING_NOT_EDITABLE"));
    }

    @Test
    void 폴더_내_회의는_시작_시각_최신순으로_나눠_받는다() throws Exception {
        String agenda = "{\"title\":\"안건\",\"decisionPoints\":[\"결정\"]}";
        for (String day : List.of("2026-10-05", "2026-10-25", "2026-10-15")) {
            call(owner, post("/api/folders/" + folderId + "/meetings"),
                    meetingJson(day, day + "T10:00:00", day + "T11:00:00", owner, List.of(), agenda))
                    .andExpect(status().isCreated());
        }

        call(member, get("/api/folders/" + folderId + "/meetings").param("size", "2"), null)
                .andExpect(jsonPath("$.data.items[*].title", contains("2026-10-25", "2026-10-15")))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        call(member, get("/api/folders/" + folderId + "/meetings").param("size", "2").param("page", "1"), null)
                .andExpect(jsonPath("$.data.items[*].title", contains("2026-10-05")))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        call(outsider, get("/api/folders/" + folderId + "/meetings"), null).andExpect(status().isForbidden());
    }

    @Test
    void 캘린더는_내가_들어갈_수_있는_모든_폴더의_회의를_보여준다() throws Exception {
        Long otherWorkspace = newId();
        Long otherFolder = folder(otherWorkspace, "다른 팀", member);
        Long hiddenFolder = folder(workspaceId, "비공개", owner);
        String agenda = "{\"title\":\"안건\",\"decisionPoints\":[\"결정\"]}";
        call(owner, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("기획 회의", "2026-11-03T10:00:00", "2026-11-03T11:00:00", owner, List.of(), agenda));
        call(member, post("/api/folders/" + otherFolder + "/meetings"),
                meetingJson("다른 팀 회의", "2026-11-02T10:00:00", "2026-11-02T11:00:00", member, List.of(), agenda));
        call(owner, post("/api/folders/" + hiddenFolder + "/meetings"),
                meetingJson("비공개 회의", "2026-11-04T10:00:00", "2026-11-04T11:00:00", owner, List.of(), agenda));
        call(member, post("/api/folders/" + otherFolder + "/meetings"),
                meetingJson("기간 밖", "2026-12-20T10:00:00", "2026-12-20T11:00:00", member, List.of(), agenda));

        call(member, get("/api/meetings/calendar").param("from", "2026-11-01").param("to", "2026-11-30"), null)
                .andExpect(jsonPath("$.data[*].title", contains("다른 팀 회의", "기획 회의")))
                .andExpect(jsonPath("$.data[1].workspaceName").value("워크스페이스" + workspaceId))
                .andExpect(jsonPath("$.data[1].folderName").value("기획"));
        call(member, get("/api/meetings/calendar").param("from", "2026-11-01").param("to", "2026-12-31"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALENDAR_RANGE_INVALID"));
    }

    @Test
    void 만든_사람은_같은_워크스페이스의_폴더로_회의를_옮길_수_있다() throws Exception {
        Long target = folder(workspaceId, "보관", owner);
        Long otherWorkspaceFolder = folder(newId(), "남의 폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);

        call(member, put("/api/meetings/" + meetingId + "/folder"), "{\"folderId\":%d}".formatted(target))
                .andExpect(jsonPath("$.code").value("NOT_MEETING_OWNER"));
        call(owner, put("/api/meetings/" + meetingId + "/folder"), "{\"folderId\":%d}".formatted(otherWorkspaceFolder))
                .andExpect(jsonPath("$.code").value("FOLDER_MOVE_INVALID"));
        call(owner, put("/api/meetings/" + meetingId + "/folder"), "{\"folderId\":%d}".formatted(target))
                .andExpect(status().isOk());

        call(owner, get("/api/folders/" + target + "/meetings"), null)
                .andExpect(jsonPath("$.data.items[0].meetingId").value(meetingId));
        call(member, get("/api/meetings/" + meetingId), null).andExpect(status().isForbidden());
        assertThat(outboxContains(MeetingMoved.TYPE,
                "\"fromFolderId\":%d,\"toFolderId\":%d".formatted(folderId, target))).isTrue();
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/api/meetings/1")).andExpect(status().isUnauthorized());
    }
}
