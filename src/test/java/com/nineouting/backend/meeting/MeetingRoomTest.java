package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 회의방 WebSocket: 참석자 화면에 전사 텍스트·수정본을 실시간으로 보낸다 (REQ-079, 083) */
class MeetingRoomTest extends ApiTestSupport {

    private static final long WAIT_SECONDS = 10;

    @LocalServerPort
    private int port;

    private Long owner;
    private Long writer;
    private Long attendee;
    private Long folderMember;
    private Long meetingId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newId();
        writer = newId();
        attendee = newId();
        folderMember = newId();
        Long folderId = folder(newId(), "기획", owner, writer, attendee, folderMember);
        meetingId = createMeeting(folderId, owner, writer, writer, attendee);
    }

    @Test
    void 참석자는_입장권으로_회의방에_들어와_전사_텍스트와_수정본을_실시간으로_받는다() throws Exception {
        call(attendee, post(roomTicketPath()), null).andExpect(jsonPath("$.code").value("MEETING_NOT_IN_PROGRESS"));
        start();
        call(folderMember, post(roomTicketPath()), null).andExpect(jsonPath("$.code").value("NOT_MEETING_ATTENDEE"));

        Room room = Room.connect(port, meetingId, roomTicket(attendee));
        postTranscripts("""
                {"seq":1,"rev":1,"text":"배포는","startMs":1000,"final":false}""");
        assertThat((Object) JsonPath.read(room.next(), "$.type")).isEqualTo("transcript");
        postTranscripts("""
                {"seq":1,"rev":2,"text":"배포는 목요일","startMs":1000,"endMs":2000,"final":true,"kind":null}""");
        String confirmed = room.next();
        assertThat((Object) JsonPath.read(confirmed, "$.line.text")).isEqualTo("배포는 목요일");
        assertThat((Object) JsonPath.read(confirmed, "$.line.final")).isEqualTo(true);
        // 무시된 줄(확정 뒤의 중간 결과)은 보내지 않는다
        postTranscripts("""
                {"seq":1,"rev":3,"text":"덮어쓰기","startMs":1000,"final":false}""");

        call(writer, patch(linePath(1)), "{\"text\":\"배포는 다음 주 목요일\"}").andExpect(status().isOk());
        String edited = room.next();
        assertThat((Object) JsonPath.read(edited, "$.type")).isEqualTo("transcriptEdited");
        assertThat((Object) JsonPath.read(edited, "$.line.rev")).isEqualTo(3);
        call(writer, delete(linePath(1)), null).andExpect(status().isOk());
        String deleted = room.next();
        assertThat((Object) JsonPath.read(deleted, "$.type")).isEqualTo("transcriptDeleted");
        assertThat((Object) JsonPath.read(deleted, "$.seq")).isEqualTo(1);

        // 멈춤·재개는 회의록 작성자만, 참석자 화면에 알린다 (REQ-084)
        call(attendee, post("/api/meetings/" + meetingId + "/pause"), null)
                .andExpect(jsonPath("$.code").value("NOT_MEETING_WRITER"));
        call(writer, post("/api/meetings/" + meetingId + "/resume"), null)
                .andExpect(jsonPath("$.code").value("MEETING_NOT_PAUSED"));
        call(writer, post("/api/meetings/" + meetingId + "/pause"), null).andExpect(status().isOk());
        assertThat((Object) JsonPath.read(room.next(), "$.type")).isEqualTo("paused");
        call(writer, post("/api/meetings/" + meetingId + "/pause"), null)
                .andExpect(jsonPath("$.code").value("MEETING_ALREADY_PAUSED"));
        call(attendee, get("/api/meetings/" + meetingId), null)
                .andExpect(jsonPath("$.data.paused").value(true))
                .andExpect(jsonPath("$.data.pausedAt").isNotEmpty());
        call(writer, post("/api/meetings/" + meetingId + "/resume"), null).andExpect(status().isOk());
        assertThat((Object) JsonPath.read(room.next(), "$.type")).isEqualTo("resumed");
        call(attendee, get("/api/meetings/" + meetingId), null)
                .andExpect(jsonPath("$.data.paused").value(false))
                .andExpect(jsonPath("$.data.pausedSeconds").isNumber());

        // 회의를 마무리하면 알리고 연결을 닫는다
        call(writer, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());
        assertThat((Object) JsonPath.read(room.next(), "$.type")).isEqualTo("finished");
        assertThat(room.closed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 입장권은_한_번만_쓰고_다른_회의나_음성_연결용으로는_들어올_수_없다() throws Exception {
        start();
        String ticket = roomTicket(attendee);
        Room.connect(port, meetingId, ticket).socket.abort();
        assertRejected(meetingId, ticket);
        assertRejected(meetingId, "unknown");
        assertRejected(meetingId + 1, roomTicket(attendee));
        String sttTicket = JsonPath.read(call(writer, post("/api/meetings/" + meetingId + "/stt-ticket"), null)
                .andReturn().getResponse().getContentAsString(), "$.data.ticket");
        assertRejected(meetingId, sttTicket);
        // 회의방 입장권으로 음성 연결 확인도 할 수 없다
        mockMvc.perform(post("/internal/stt-tickets/" + roomTicket(writer) + "/consume"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ai_service가_멈추면_회의방에_AI_일시_중단을_알리고_돌아오면_다시_알린다() throws Exception {
        start();
        Room room = Room.connect(port, meetingId, roomTicket(attendee));
        assertThat(room.nextAiStatus()).isTrue();   // 들어오면 지금 상태를 받는다
        FakeAiService.respondWith(503);
        try {
            assertThat(room.nextAiStatus()).isFalse();
        } finally {
            FakeAiService.respondWith(200);
        }
        assertThat(room.nextAiStatus()).isTrue();
        room.socket.abort();
    }

    @Test
    void ai_service가_보낸_회의_중_요약은_최신_것만_남기고_회의방에_바로_보낸다() throws Exception {
        putLiveSummary(meetingId.toString(), 100, false, "시작 전").andExpect(status().isConflict());
        start();
        Room room = Room.connect(port, meetingId, roomTicket(attendee));
        call(attendee, get(liveSummaryPath()), null).andExpect(jsonPath("$.data").doesNotExist());

        putLiveSummary(meetingId.toString(), 100, false, "배포는 목요일로 하자는 제안").andExpect(status().isNoContent());
        String relayed = room.next();
        assertThat((Object) JsonPath.read(relayed, "$.type")).isEqualTo("liveSummary");
        assertThat((Object) JsonPath.read(relayed, "$.liveSummary.summary[0].text")).isEqualTo("배포는 목요일로 하자는 제안");
        assertThat((Object) JsonPath.read(relayed, "$.liveSummary.summary[0].evidence[0].start")).isEqualTo(1.5);

        // 늦게 온 옛 요약은 버린다 (회의방에도 안 보낸다)
        putLiveSummary(meetingId.toString(), 50, false, "옛 요약").andExpect(status().isNoContent());
        putLiveSummary(meetingId.toString(), 200, false, "새 요약").andExpect(status().isNoContent());
        assertThat((Object) JsonPath.read(room.next(), "$.liveSummary.version")).isEqualTo(200);
        call(attendee, get(liveSummaryPath()), null)
                .andExpect(jsonPath("$.data.version").value(200))
                .andExpect(jsonPath("$.data.summary[0].text").value("새 요약"))
                .andExpect(jsonPath("$.data.summary[0].topic").value("배포"))
                .andExpect(jsonPath("$.data.final").value(false));
        call(folderMember, get(liveSummaryPath()), null).andExpect(jsonPath("$.code").value("NOT_MEETING_ATTENDEE"));

        // 마지막 요약은 회의가 끝난 뒤에도 받는다
        call(writer, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());
        putLiveSummary(meetingId.toString(), 300, true, "마지막 요약").andExpect(status().isNoContent());
        call(attendee, get(liveSummaryPath()), null).andExpect(jsonPath("$.data.final").value(true));

        putLiveSummary("m_9999", 400, false, "다른 회의").andExpect(status().isBadRequest());
        mockMvc.perform(put("/internal/meetings/" + meetingId + "/live-summary")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isBadRequest());
    }

    private String liveSummaryPath() {
        return "/api/meetings/" + meetingId + "/live-summary";
    }

    private ResultActions putLiveSummary(String bodyMeetingId, long version, boolean isFinal, String text)
            throws Exception {
        return mockMvc.perform(put("/internal/meetings/" + meetingId + "/live-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"schemaVersion":"0.1","meetingId":"%s","version":%d,"atMs":134200,"final":%s,
                         "summary":[{"text":"%s","topic":"배포","evidence":[{"start":1.5,"end":2.0}]}]}"""
                        .formatted(bodyMeetingId, version, isFinal, text)));
    }

    private void assertRejected(Long targetMeetingId, String ticket) {
        // 연결을 받지 않고 401로 답한다
        assertThatThrownBy(() -> Room.connect(port, targetMeetingId, ticket)).hasStackTraceContaining("401");
    }

    private void start() throws Exception {
        call(writer, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
    }

    private String roomTicketPath() {
        return "/api/meetings/" + meetingId + "/room-ticket";
    }

    private String roomTicket(Long userId) throws Exception {
        return JsonPath.read(call(userId, post(roomTicketPath()), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.ticket");
    }

    private String linePath(int seq) {
        return "/api/meetings/" + meetingId + "/transcripts/" + seq;
    }

    private void postTranscripts(String segment) throws Exception {
        mockMvc.perform(post("/internal/meetings/" + meetingId + "/transcripts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"segments\":[" + segment + "]}"))
                .andExpect(status().isNoContent());
    }

    /** 브라우저처럼 회의방에 붙어 받은 메시지를 쌓는다 */
    private static final class Room implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        /** ai-service 상태(aiStatus)는 다른 테스트의 장애 흉내와 겹칠 수 있어 따로 모은다 */
        private final BlockingQueue<Boolean> aiStatuses = new LinkedBlockingQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        static Room connect(int port, Long meetingId, String ticket) {
            Room room = new Room();
            try {
                room.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                        .buildAsync(URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?ticket=" + ticket),
                                room)
                        .join();
            } catch (CompletionException e) {
                throw new IllegalStateException("회의방에 들어가지 못했습니다", e);
            }
            return room;
        }

        boolean nextAiStatus() throws InterruptedException {
            Boolean available = aiStatuses.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(available).as("aiStatus를 받지 못했습니다").isNotNull();
            return available;
        }

        String next() throws InterruptedException {
            String message = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("회의방 메시지를 받지 못했습니다").isNotNull();
            return message;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                if ("aiStatus".equals(JsonPath.read(message, "$.type"))) {
                    aiStatuses.add(JsonPath.read(message, "$.available"));
                } else {
                    messages.add(message);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.countDown();
            return null;
        }
    }
}
