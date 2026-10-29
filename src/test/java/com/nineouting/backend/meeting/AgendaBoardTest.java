package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.meeting.service.AgendaBoardService;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 안건 보드 (REQ-085~097, 124~126, ai-service docs/api/agenda-*.md) */
class AgendaBoardTest extends ApiTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    private AgendaBoardService agendaBoardService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long writer;
    private Long attendee;
    private Long meetingId;
    private Long agendaId;
    private Long decisionPointId;

    @BeforeEach
    void setUp() throws Exception {
        writer = newId();
        attendee = newId();
        Long folderId = folder(newId(), "기획", writer, attendee);
        meetingId = id(call(writer, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("주간 회의", "2026-10-29T14:00:00", "2026-10-29T15:00:00", writer, List.of(attendee),
                        "{\"title\":\"배포 일정\",\"decisionPoints\":[\"배포 날짜\",\"QA 담당\"]}"))
                .andExpect(status().isCreated()), "$.data.meetingId");
        String detail = call(writer, get("/api/meetings/" + meetingId), null).andReturn().getResponse().getContentAsString();
        agendaId = ((Number) JsonPath.read(detail, "$.data.agendas[0].agendaId")).longValue();
        decisionPointId = ((Number) JsonPath.read(detail, "$.data.agendas[0].decisionPoints[0].decisionPointId")).longValue();
    }

    @Test
    void 회의를_시작하면_안건_목록을_결정할_사항_참석자와_함께_먼저_보낸다() throws Exception {
        start();

        String agendas = await("agendas", body -> true);
        assertThat(JsonPath.<String>read(agendas, "$.meetingId")).isEqualTo(meetingId.toString());
        assertThat(JsonPath.<Integer>read(agendas, "$.agendas[0].agendaId").longValue()).isEqualTo(agendaId);
        assertThat(JsonPath.<List<String>>read(agendas, "$.agendas[0].decisionPoints[*].text")).containsExactly("배포 날짜", "QA 담당");
        assertThat(JsonPath.<Integer>read(agendas, "$.agendas[0].decisionPoints[0].position")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(agendas, "$.attendees[*].name")).contains("user" + writer, "user" + attendee);
        long firstVersion = JsonPath.<Number>read(agendas, "$.version").longValue();

        // 회의 중 안건 추가 → 더 큰 버전으로 전체 목록을 다시 보낸다 (REQ-090)
        call(writer, post("/api/meetings/" + meetingId + "/agendas"),
                "{\"title\":\"예산\",\"decisionPoints\":[\"한도\"]}").andExpect(status().isCreated());
        String again = await("agendas", body -> JsonPath.<List<?>>read(body, "$.agendas").size() == 2);
        assertThat(JsonPath.<Number>read(again, "$.version").longValue()).isGreaterThan(firstVersion);
        assertThat(JsonPath.<Boolean>read(again, "$.agendas[1].addedDuringMeeting")).isTrue();
    }

    @Test
    void 줄별_분류와_보드를_받아_저장하고_낮은_버전은_무시한다() throws Exception {
        start();
        internalPut("agenda-classification", """
                {"schemaVersion":"0.1","meetingId":"%d","version":200,"atMs":1000,"final":false,"currentAgendaId":%d,
                 "lines":[{"seq":1,"agendaId":%d},{"seq":2,"agendaId":null}]}""".formatted(meetingId, agendaId, agendaId))
                .andExpect(status().isNoContent());
        internalPut("agenda-classification", """
                {"meetingId":"%d","version":100,"currentAgendaId":null,"lines":[{"seq":1,"agendaId":null}]}"""
                .formatted(meetingId)).andExpect(status().isNoContent());
        call(attendee, get("/api/meetings/" + meetingId + "/line-agendas"), null)
                .andExpect(jsonPath("$.data[0].agendaId").value(agendaId))
                .andExpect(jsonPath("$.data[1].agendaId").doesNotExist());

        internalPut("agenda-board", board(300, "DECISION_CHECK", null)).andExpect(status().isNoContent());
        internalPut("agenda-board", board(250, "DISCUSSING", null)).andExpect(status().isNoContent());
        call(attendee, get("/api/meetings/" + meetingId + "/agenda-board"), null)
                .andExpect(jsonPath("$.data.version").value(300))
                .andExpect(jsonPath("$.data.currentAgendaId").value(agendaId))
                .andExpect(jsonPath("$.data.board.agendas[0].status").value("DECISION_CHECK"))
                .andExpect(jsonPath("$.data.board.agendas[0].decisionPoints[0].decisionText").value("30일 배포"));
        // 보드의 안건 상태는 안건 표에도 둔다 (REQ-088)
        call(writer, get("/api/meetings/" + meetingId), null)
                .andExpect(jsonPath("$.data.agendas[0].status").value("DECISION_CHECK"));

        internalPut("agenda-board", board(400, "DECIDED", null).replace("\"meetingId\":\"" + meetingId, "\"meetingId\":\"999"))
                .andExpect(status().isBadRequest());
        call(newId(), get("/api/meetings/" + meetingId + "/agenda-board"), null).andExpect(status().isForbidden());
    }

    @Test
    void 회의록_작성자의_고치기를_ai_service로_보낸다() throws Exception {
        start();
        internalPost("/internal/meetings/" + meetingId + "/transcripts",
                "{\"segments\":[{\"seq\":5,\"rev\":1,\"text\":\"QA는 수요일까지\",\"startMs\":1000,\"endMs\":2000,\"final\":true}]}");

        call(attendee, put("/api/meetings/" + meetingId + "/transcripts/5/agenda"), "{\"agendaId\":" + agendaId + "}")
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        call(writer, put("/api/meetings/" + meetingId + "/transcripts/5/agenda"), "{\"agendaId\":" + agendaId + "}")
                .andExpect(jsonPath("$.data.corrected").value(true));
        String corrections = await("agenda-corrections", body -> true);
        assertThat(JsonPath.<Integer>read(corrections, "$.corrections[0].seq")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(corrections, "$.corrections[0].rev")).isPositive();

        call(writer, put("/api/meetings/" + meetingId + "/agenda-board/summary/k1-1"),
                "{\"agendaId\":%d,\"text\":\"금요일로 미루자는 의견\",\"evidenceSeqs\":[5]}".formatted(agendaId))
                .andExpect(status().isOk());
        call(writer, put("/api/meetings/" + meetingId + "/agenda-board/summary/k1-2"),
                "{\"agendaId\":%d,\"text\":\"근거 없음\",\"evidenceSeqs\":[]}".formatted(agendaId))
                .andExpect(status().isBadRequest());
        call(writer, post("/api/meetings/" + meetingId + "/agenda-board/opinions/k1-8/confirm"),
                "{\"agendaId\":%d}".formatted(agendaId)).andExpect(status().isOk());
        call(writer, put("/api/meetings/" + meetingId + "/agenda-board/todos/k1-11"),
                "{\"agendaId\":%d,\"task\":\"체크리스트 작성\",\"assigneeName\":\"user%d\",\"assigneeUserId\":%d,\"evidenceSeqs\":[5]}"
                        .formatted(agendaId, attendee, attendee)).andExpect(status().isOk());
        call(writer, put("/api/meetings/" + meetingId + "/agendas/" + agendaId + "/decision-points/" + decisionPointId + "/decision"),
                "{\"decisionText\":\"10월 31일 배포\",\"evidenceSeqs\":[5]}").andExpect(status().isOk());

        String summary = await("agenda-summary-edits", body -> true);
        assertThat(JsonPath.<String>read(summary, "$.edits[0].action")).isEqualTo("edit");
        assertThat(JsonPath.<String>read(summary, "$.edits[0].itemId")).isEqualTo("k1-1");
        String items = await("agenda-item-edits", body -> body.contains("체크리스트 작성"));
        assertThat(JsonPath.<List<String>>read(items, "$.edits[*].target")).contains("todo");
        String decision = await("decision-edits", body -> true);
        assertThat(JsonPath.<Integer>read(decision, "$.edits[0].decisionPointId").longValue()).isEqualTo(decisionPointId);
        assertThat(JsonPath.<String>read(decision, "$.edits[0].decisionText")).isEqualTo("10월 31일 배포");
    }

    @Test
    void 새_안건_제안은_무시하거나_30초가_지나면_무시로_보낸다() throws Exception {
        start();
        call(writer, post("/api/meetings/" + meetingId + "/off-topic/dismiss"), null).andExpect(status().isOk());
        String dismissed = await("off-topic-actions", body -> true);
        assertThat(JsonPath.<String>read(dismissed, "$.action")).isEqualTo("dismiss");
        assertThat(dismissed).doesNotContain("atMs");   // 평소에는 ai-service가 받은 시점으로 처리한다

        internalPut("agenda-board", board(500, "DISCUSSING",
                "{\"stage\":\"SUGGEST\",\"detectedAt\":\"2026-10-29T05:21:00Z\",\"detectedAtMs\":60000,\"suggestedTitle\":\"채용\",\"evidenceSeqs\":[]}"))
                .andExpect(status().isNoContent());
        agendaBoardService.dismissStaleOffTopics();
        assertThat(FakeAiService.received(meetingId, "off-topic-actions")).hasSize(1);   // 아직 30초가 안 됐다

        jdbcTemplate.update("update agenda_states set off_topic_suggested_at = off_topic_suggested_at - interval '31 seconds' "
                + "where meeting_id = ?", meetingId);
        agendaBoardService.dismissStaleOffTopics();
        await("off-topic-actions", body -> JsonPath.<Integer>read(body, "$.rev") > JsonPath.<Integer>read(dismissed, "$.rev"));
        agendaBoardService.dismissStaleOffTopics();
        Thread.sleep(1000);
        assertThat(FakeAiService.received(meetingId, "off-topic-actions")).hasSize(2);   // 같은 제안은 한 번만
    }

    @Test
    void 회의_끝내기와_이어서_논의하기를_보내고_끝난_뒤_오늘_회의_요약을_점검한다() throws Exception {
        start();
        call(writer, delete("/api/meetings/" + meetingId + "/end-request"), null)
                .andExpect(jsonPath("$.code").value("END_NOT_REQUESTED"));
        call(writer, post("/api/meetings/" + meetingId + "/end-request"), null).andExpect(status().isOk());
        await("end-requests", body -> body.contains("\"request\""));
        call(writer, delete("/api/meetings/" + meetingId + "/end-request"), null).andExpect(status().isOk());
        await("end-requests", body -> body.contains("\"cancel\""));

        call(writer, put("/api/meetings/" + meetingId + "/today-summary"), "{\"agendas\":[]}")
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FINISHED"));
        call(writer, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());
        // 마지막 보드는 회의가 끝난 뒤에 온다
        internalPut("agenda-board", board(600, "UNDECIDED", null).replace("\"todaySummary\":null",
                "\"todaySummary\":{\"requestRev\":null,\"final\":true,\"agendas\":[{\"agendaId\":%d,\"status\":\"UNDECIDED\",\"statusReason\":null,\"summary\":[],\"opinions\":[],\"todos\":[]}]}"
                        .formatted(agendaId))).andExpect(status().isNoContent());

        call(attendee, get("/api/meetings/" + meetingId + "/today-summary"), null)
                .andExpect(jsonPath("$.data.final").value(true))
                .andExpect(jsonPath("$.data.todaySummary.agendas[0].status").value("UNDECIDED"));
        String revised = "{\"agendas\":[{\"agendaId\":%d,\"status\":\"UNDECIDED\",\"statusReason\":null,\"summary\":[],\"opinions\":[],"
                .formatted(agendaId) + "\"todos\":[{\"id\":\"u-1\",\"task\":\"회의실 예약\",\"evidenceSeqs\":[],\"edited\":true}]}]}";
        call(attendee, put("/api/meetings/" + meetingId + "/today-summary"), revised).andExpect(status().isForbidden());
        call(writer, put("/api/meetings/" + meetingId + "/today-summary"), revised).andExpect(status().isOk());

        mockMvc.perform(get("/internal/meetings/" + meetingId + "/draft-source"))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.agendas[0].title").value("배포 일정"))
                .andExpect(jsonPath("$.data.agendas[0].decisionPoints[*].text").value(contains("배포 날짜", "QA 담당")))
                .andExpect(jsonPath("$.data.agendas[0].decisionPoints[1].position").value(2))
                .andExpect(jsonPath("$.data.summary.agendas[0].todos[0].task").value("회의실 예약"));
    }

    @Test
    void ai_service가_재시작하면_안건_목록과_고친_것을_줄_묶음보다_먼저_다시_보낸다() throws Exception {
        start();
        await("agendas", body -> true);
        internalPost("/internal/meetings/" + meetingId + "/transcripts",
                "{\"segments\":[{\"seq\":1,\"rev\":1,\"text\":\"첫 줄\",\"startMs\":1000,\"endMs\":2000,\"final\":true}]}");
        awaitKind("batches", 1);
        call(writer, put("/api/meetings/" + meetingId + "/agendas/" + agendaId + "/decision-points/" + decisionPointId + "/decision"),
                "{\"decisionText\":\"30일 배포\",\"evidenceSeqs\":[1]}").andExpect(status().isOk());
        call(writer, post("/api/meetings/" + meetingId + "/off-topic/dismiss"), null).andExpect(status().isOk());
        awaitKind("decision-edits", 1);
        awaitKind("off-topic-actions", 1);

        // ai-service 재시작: 다음 줄 묶음에 1번부터 다시 달라고 답한다
        FakeAiService.conflictOnce(1);
        internalPost("/internal/meetings/" + meetingId + "/transcripts",
                "{\"segments\":[{\"seq\":2,\"rev\":1,\"text\":\"둘째 줄\",\"startMs\":3000,\"endMs\":4000,\"final\":true}]}");
        awaitKind("agendas", 2);
        awaitKind("decision-edits", 2);
        String replayed = awaitKind("off-topic-actions", 2);
        assertThat(JsonPath.<Integer>read(replayed, "$.atMs")).isEqualTo(2000);   // 다시 보낼 때는 무시한 시각을 담는다
        awaitKind("batches", 3);
        List<String> kinds = FakeAiService.receivedKinds(meetingId);
        int resentBatch = kinds.lastIndexOf("batches");
        assertThat(kinds.lastIndexOf("agendas")).isLessThan(resentBatch);
        assertThat(kinds.lastIndexOf("decision-edits")).isLessThan(resentBatch);
    }

    private void start() throws Exception {
        call(writer, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
    }

    private String board(long version, String status, String offTopic) {
        return """
                {"meetingId":"%d","version":%d,"basedOnSeq":5,"currentAgendaId":%d,
                 "agendas":[{"agendaId":%d,"status":"%s","statusReason":{"text":"이유","evidenceSeqs":[1]},
                   "decisionPoints":[{"decisionPointId":%d,"status":"DECIDED","decisionText":"30일 배포","evidenceSeqs":[1],"edited":false,"needsCheck":false}],
                   "summary":[],"opinions":[],"todos":[]}],
                 "offTopic":%s,"leftover":null,"todaySummary":null}"""
                .formatted(meetingId, version, agendaId, agendaId, status, decisionPointId, offTopic);
    }

    private ResultActions internalPut(String kind, String body) throws Exception {
        return internal(put("/internal/meetings/" + meetingId + "/" + kind), body);
    }

    private ResultActions internalPost(String path, String body) throws Exception {
        return internal(post(path), body).andExpect(status().is2xxSuccessful());
    }

    private ResultActions internal(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 조건에 맞는 가장 나중 요청 */
    private String await(String kind, Predicate<String> matches) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<String> found = FakeAiService.received(meetingId, kind).stream().filter(matches).toList();
            if (!found.isEmpty()) {
                return found.getLast();
            }
            Thread.sleep(100);
        }
        throw new AssertionError(kind + " 요청을 받지 못했습니다. 받은 것: " + FakeAiService.receivedKinds(meetingId));
    }

    /** kind 요청이 count개 이상 올 때까지 기다려 마지막 것을 돌려준다 */
    private String awaitKind(String kind, int count) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<String> found = FakeAiService.received(meetingId, kind);
            if (found.size() >= count) {
                return found.getLast();
            }
            Thread.sleep(100);
        }
        throw new AssertionError(kind + " 요청이 " + count + "개 오지 않았습니다. 받은 것: " + FakeAiService.receivedKinds(meetingId));
    }
}
