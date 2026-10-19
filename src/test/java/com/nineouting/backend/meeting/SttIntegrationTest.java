package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** STT 연동: 음성 연결 입장권, 전사 텍스트 저장·수정, ai-service로 밀어 주기 (docs/services/ai-integration.md 2장) */
class SttIntegrationTest extends ApiTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private Long owner;
    private Long writer;
    private Long folderMember;
    private Long meetingId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newId();
        writer = newId();
        folderMember = newId();
        Long folderId = folder(newId(), "기획", owner, writer, folderMember);
        meetingId = createMeeting(folderId, owner, writer, writer);
    }

    @Test
    void 입장권은_회의록_작성자가_진행_중에만_받고_한_번만_쓸_수_있다() throws Exception {
        call(writer, post("/api/meetings/" + meetingId + "/stt-ticket"), null)
                .andExpect(jsonPath("$.code").value("MEETING_NOT_IN_PROGRESS"));
        start();
        call(owner, post("/api/meetings/" + meetingId + "/stt-ticket"), null)
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        String ticket = data(call(writer, post("/api/meetings/" + meetingId + "/stt-ticket"), null)
                .andExpect(jsonPath("$.data.expiresIn").value(30)), "$.data.ticket");

        internal("/internal/stt-tickets/" + ticket + "/consume", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(meetingId))
                .andExpect(jsonPath("$.data.userId").value(writer));
        internal("/internal/stt-tickets/" + ticket + "/consume", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_STT_TICKET"));
        internal("/internal/stt-tickets/unknown/consume", null)
                .andExpect(jsonPath("$.code").value("INVALID_STT_TICKET"));
    }

    @Test
    void STT_줄은_rev가_클_때만_바꾸고_확정된_줄은_다시_바꾸지_않는다() throws Exception {
        internal(transcripts(), segments(segment(1, 1, "배포는", 125300, null, false)))
                .andExpect(jsonPath("$.code").value("MEETING_NOT_IN_PROGRESS"));
        start();

        internal(transcripts(), segments(segment(1, 1, "배포는", 125300, null, false))).andExpect(status().isNoContent());
        internal(transcripts(), segments(segment(1, 2, "배포는 목요일로 하죠", 125300, 128900L, true)));
        // 늦게 온 옛 글자, 확정 뒤의 중간 결과는 무시한다
        internal(transcripts(), segments(segment(1, 1, "옛 글자", 125300, null, false)));
        internal(transcripts(), segments(segment(1, 3, "덮어쓰기", 125300, null, false)));
        // 놓친 말을 앞 시각에 끼워 넣은 보완 줄
        internal(transcripts(), segments("""
                {"seq":2,"rev":1,"speakerLabel":"화자 2","text":"네","startMs":124800,"endMs":125100,"final":true,
                 "lowConfidence":true,"kind":"backchannel","recovered":true}"""));

        call(writer, get(transcripts().replace("/internal", "/api")), null)
                .andExpect(jsonPath("$.data[*].seq", contains(2, 1)))
                .andExpect(jsonPath("$.data[0].kind").value("backchannel"))
                .andExpect(jsonPath("$.data[0].recovered").value(true))
                .andExpect(jsonPath("$.data[0].lowConfidence").value(true))
                .andExpect(jsonPath("$.data[1].text").value("배포는 목요일로 하죠"))
                .andExpect(jsonPath("$.data[1].rev").value(2))
                .andExpect(jsonPath("$.data[1].final").value(true));
        call(folderMember, get(transcripts().replace("/internal", "/api")), null)
                .andExpect(jsonPath("$.code").value("NOT_MEETING_ATTENDEE"));

        internal(transcripts(), segments(segment(0, 1, "사용자 줄 번호", 0, null, true)))
                .andExpect(status().isBadRequest());
        internal(transcripts(), segments("""
                {"seq":9,"rev":1,"text":"음","startMs":0,"final":true,"kind":"cough"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회의록_작성자는_확정된_줄만_고치고_지운다() throws Exception {
        start();
        internal(transcripts(), segments(segment(1, 1, "벡터 디비로", 1000, 2000L, true),
                segment(2, 1, "말하는 중", 3000, null, false)));
        String line = transcripts().replace("/internal", "/api") + "/";

        call(owner, patch(line + 1), "{\"text\":\"벡터DB로 가죠\"}")
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        call(writer, patch(line + 2), "{\"text\":\"고침\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSCRIPT_NOT_EDITABLE"));
        call(writer, patch(line + 1), "{\"text\":\" 벡터DB로 가죠 \"}")
                .andExpect(jsonPath("$.data.text").value("벡터DB로 가죠"))
                .andExpect(jsonPath("$.data.rev").value(2))
                .andExpect(jsonPath("$.data.lowConfidence").value(false));
        // 작성자가 고친 뒤 STT가 같은 번호를 다시 보내도 수정본을 지킨다
        internal(transcripts(), segments(segment(1, 2, "벡터 디비로", 1000, 2000L, true)));
        call(writer, delete(line + 1), null).andExpect(status().isOk());
        call(writer, patch(line + 1), "{\"text\":\"다시\"}").andExpect(jsonPath("$.code").value("TRANSCRIPT_NOT_EDITABLE"));
        call(writer, get(transcripts().replace("/internal", "/api")), null)
                .andExpect(jsonPath("$.data[*].seq", contains(2)));
        call(writer, patch(line + 99), "{\"text\":\"없음\"}").andExpect(status().isNotFound());
    }

    @Test
    void 회의_알림은_바로_줄_묶음은_주기마다_ai_service로_밀어_준다() throws Exception {
        start();
        internal(events(), "{\"event\":\"start\",\"atMs\":0}").andExpect(status().isNoContent());
        internal(events(), "{\"event\":\"start\",\"atMs\":0}");   // 같은 알림은 한 번만
        internal(events(), "{\"event\":\"stop\",\"atMs\":0}").andExpect(status().isBadRequest());
        internal(transcripts(), segments(segment(1, 1, "배포는 다음 주", 1000, 2000L, true),
                segment(2, 1, "말하는 중", 3000, null, false)));

        String event = awaitOne(() -> FakeAiService.received(meetingId, "events"));
        assertThat((Object) JsonPath.read(event, "$.event")).isEqualTo("start");
        assertThat((Object) JsonPath.read(event, "$.meetingId")).isEqualTo(meetingId.toString());
        assertThat(JsonPath.<String>read(event, "$.wall")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00");
        String first = awaitBatch(1);
        assertThat(JsonPath.<List<Integer>>read(first, "$.lines[*].seq")).containsExactly(1);   // 중간 결과는 넘기지 않는다
        assertThat(JsonPath.<List<?>>read(first, "$.edits")).isEmpty();
        assertThat(first).doesNotContain("speakerLabel");

        // 넘긴 줄을 고치면 다음 묶음의 edits로 간다
        call(writer, patch(transcripts().replace("/internal", "/api") + "/1"), "{\"text\":\"배포는 다음 주 목요일\"}");
        String second = awaitBatch(2);
        assertThat(JsonPath.<List<Integer>>read(second, "$.edits[*].rev")).containsExactly(2);
        assertThat(JsonPath.<List<String>>read(second, "$.edits[*].text")).containsExactly("배포는 다음 주 목요일");

        // ai-service가 멈춘 동안의 줄은 모아 두었다가 한 묶음으로 보낸다 (REQ-111)
        FakeAiService.respondWith(503);
        try {
            internal(transcripts(), segments(segment(3, 1, "셋째", 5000, 6000L, true)));
            Thread.sleep(2500);
            internal(transcripts(), segments(segment(4, 1, "넷째", 7000, 8000L, true)));
        } finally {
            FakeAiService.respondWith(200);
        }
        String third = awaitBatch(3);
        assertThat(JsonPath.<List<Integer>>read(third, "$.lines[*].seq")).containsExactly(3, 4);

        // ai-service가 빠진 번호를 알려 주면 그 번호부터 저장해 둔 묶음을 다시 보낸다
        FakeAiService.conflictOnce(2);
        call(writer, delete(transcripts().replace("/internal", "/api") + "/3"), null);
        String fourth = awaitBatch(4);
        assertThat(JsonPath.<List<Boolean>>read(fourth, "$.edits[*].deleted")).containsExactly(true);
        assertThat(batchNumbers()).containsExactly(1, 2, 3, 2, 3, 4);

        // 종료 알림은 남은 줄을 보낸 뒤에 간다
        internal(transcripts(), segments(segment(5, 1, "마지막 말", 9000, 9500L, true)));
        internal(events(), "{\"event\":\"end\",\"atMs\":9600}");
        awaitBatch(5);
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (FakeAiService.received(meetingId, "events").size() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(FakeAiService.receivedKinds(meetingId)).endsWith("batches", "events");
    }

    private void start() throws Exception {
        call(writer, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
    }

    private String transcripts() {
        return "/internal/meetings/" + meetingId + "/transcripts";
    }

    private String events() {
        return "/internal/meetings/" + meetingId + "/events";
    }

    /** stt-service처럼 토큰 없이 부른다 */
    private ResultActions internal(String path, String body) throws Exception {
        var request = post(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private static String segments(String... segments) {
        return "{\"segments\":[" + String.join(",", segments) + "]}";
    }

    private static String segment(int seq, int rev, String text, long startMs, Long endMs, boolean isFinal) {
        return """
                {"seq":%d,"rev":%d,"speakerLabel":"화자 1","text":"%s","startMs":%d,"endMs":%s,"final":%s}"""
                .formatted(seq, rev, text, startMs, endMs, isFinal);
    }

    private List<Integer> batchNumbers() {
        return FakeAiService.received(meetingId, "batches").stream()
                .map(body -> JsonPath.<Integer>read(body, "$.batch"))
                .toList();
    }

    /** 묶음 번호가 batch인 마지막 묶음 (다시 보낸 것이 있으면 가장 나중 것) */
    private String awaitBatch(int batch) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<String> found = FakeAiService.received(meetingId, "batches").stream()
                    .filter(body -> JsonPath.<Integer>read(body, "$.batch") == batch)
                    .toList();
            if (!found.isEmpty()) {
                return found.getLast();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("줄 묶음 " + batch + "번을 받지 못했습니다. 받은 번호: " + batchNumbers());
    }

    private static String awaitOne(Supplier<List<String>> received) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<String> found = received.get();
            if (!found.isEmpty()) {
                Thread.sleep(500);   // 같은 알림이 두 번 가지 않는지 조금 더 본다
                assertThat(received.get()).hasSize(1);
                return found.getFirst();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("ai-service가 요청을 받지 못했습니다");
    }
}
