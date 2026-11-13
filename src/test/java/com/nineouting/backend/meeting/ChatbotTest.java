package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.meeting.domain.MeetingMaterial;
import com.nineouting.backend.meeting.domain.RagStatus;
import com.nineouting.backend.meeting.repository.MeetingMaterialRepository;
import com.nineouting.backend.meeting.service.MaterialRagSync;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** REQ-185~187 회의 중 챗봇과 사전 자료 검색 상태 (docs/services/rag-chatbot.md). ai-service, minutes-service는 가짜 */
class ChatbotTest extends ApiTestSupport {

    private static final String NO_RESULT = "{\"status\":\"no_result\",\"basis\":null,"
            + "\"answer\":\"회의 자료에서 찾을 수 없어요\",\"citations\":[],\"errorCode\":null}";
    private static final String ANSWERED = """
            {"status":"answered","basis":"mixed","answer":"수요일까지 다시 보기로 했어요 [1]. 보통 목록으로 정리해요 [일반].",
             "citations":[{"ref":1,"sourceType":"live","docId":null,"title":"진행 중 회의","text":"결정: 수요일",
                           "location":{"type":"agenda","agendaId":1,"item":"decision","itemId":1,"evidenceSeqs":[],
                                       "label":"안건1 안건의 결정"}}],
             "errorCode":null}""";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    MaterialRagSync materialRagSync;

    @Autowired
    MeetingMaterialRepository materialRepository;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    TransactionTemplate transactionTemplate;

    @AfterEach
    void resetFake() {
        FakeAiService.chatAnswer(200, NO_RESULT);
        FakeAiService.respondWith(200);
        FakeAiService.rejectIngest(null);
    }

    @Test
    void ai_service에_사전_자료_저장소_허용이_없어_거절되면_실패로_굳히지_않고_다른_거절은_실패로_둔다() throws Exception {
        Long owner = newId();
        Long folderId = folder(newId(), "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        Long notConfigured = upload(meetingId, owner, "설정 전.pdf");

        FakeAiService.rejectIngest("{\"error\":\"INVALID_INPUT\",\"detail\":\"업로드 문서를 받도록 설정되지 않음 (RAG_UPLOAD_ALLOWED_HOSTS)\"}");
        materialRagSync.run();
        MeetingMaterial waiting = materialRepository.findById(notConfigured).orElseThrow();
        assertThat(waiting.getRagStatus()).isEqualTo(RagStatus.PENDING);
        assertThat(waiting.getRagErrorCode()).isEqualTo("unavailable");

        Long wrong = upload(meetingId, owner, "틀린 요청.pdf");
        FakeAiService.rejectIngest("{\"error\":\"INVALID_INPUT\",\"detail\":\"title 이 비었음\"}");
        materialRagSync.run();
        assertThat(materialRepository.findById(wrong).orElseThrow().getRagStatus()).isEqualTo(RagStatus.FAILED);
    }

    @Test
    void 올린_사전_자료를_검색용_저장소에_넣고_콜백으로_검색_상태를_보여_준다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        Long materialId = upload(meetingId, owner, "안건 정리.txt");
        MeetingMaterial saved = materialRepository.findById(materialId).orElseThrow();
        assertThat(saved.getRagStatus()).isEqualTo(RagStatus.PENDING);
        assertThat(saved.getEtag()).isNotBlank();

        materialRagSync.run();

        JsonNode sent = lastIngest(materialId);
        assertThat(sent.path("sourceType").asString()).isEqualTo("upload");
        assertThat(sent.path("workspaceId").asLong()).isEqualTo(workspaceId);
        assertThat(sent.path("docVersion").asString()).isEqualTo(saved.getEtag());
        assertThat(sent.path("title").asString()).isEqualTo("안건 정리.txt");
        assertThat(sent.path("fileUrl").asString()).startsWith("http");
        assertThat(sent.path("callbackUrl").asString()).endsWith("/internal/rag/ingest-result");
        assertThat(materialRepository.findById(materialId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.ACCEPTED);
        call(owner, get("/api/meetings/" + meetingId + "/materials"), null)
                .andExpect(jsonPath("$.data[0].searchStatus").value("PREPARING"));

        // 옛 버전의 결과는 무시한다
        callback(materialId, "old-etag", "ready", null);
        assertThat(materialRepository.findById(materialId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.ACCEPTED);

        callback(materialId, saved.getEtag(), "ready", null);
        call(owner, get("/api/meetings/" + meetingId + "/materials"), null)
                .andExpect(jsonPath("$.data[0].searchStatus").value("READY"));
    }

    @Test
    void 읽을_수_없는_파일은_다시_보내지_않고_서명_주소가_만료되면_다시_보낸다() throws Exception {
        Long owner = newId();
        Long folderId = folder(newId(), "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        Long scanned = upload(meetingId, owner, "스캔.pdf");
        Long expired = upload(meetingId, owner, "예산.xlsx");
        materialRagSync.run();

        callback(scanned, etagOf(scanned), "failed", "no_text");
        callback(expired, etagOf(expired), "failed", "url_expired");
        assertThat(materialRepository.findById(scanned).orElseThrow().getRagStatus()).isEqualTo(RagStatus.FAILED);
        assertThat(materialRepository.findById(expired).orElseThrow().getRagStatus()).isEqualTo(RagStatus.PENDING);

        materialRagSync.run();
        assertThat(ingests(scanned)).hasSize(1);
        assertThat(ingests(expired)).hasSize(2);
        call(owner, get("/api/meetings/" + meetingId + "/materials"), null)
                .andExpect(jsonPath("$.data[0].searchStatus").value("UNREADABLE"))
                .andExpect(jsonPath("$.data[1].searchStatus").value("PREPARING"));
    }

    @Test
    void ai_service가_잠시_안_되면_나중에_다시_보낸다() throws Exception {
        Long owner = newId();
        Long folderId = folder(newId(), "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        Long materialId = upload(meetingId, owner, "자료.txt");

        FakeAiService.respondWith(503);
        materialRagSync.run();
        MeetingMaterial material = materialRepository.findById(materialId).orElseThrow();
        assertThat(material.getRagStatus()).isEqualTo(RagStatus.PENDING);
        assertThat(material.getRagAttempts()).isEqualTo(1);
        assertThat(material.getRagNextAttemptAt()).isAfter(LocalDateTime.now());

        // RAG가 꺼진 ai-service(404)도 실패로 굳히지 않고 기다린다. 횟수를 넘겨도 마찬가지
        FakeAiService.respondWith(404);
        for (int i = 0; i < 6; i++) {
            transactionTemplate.executeWithoutResult(status -> materialRepository.findById(materialId).orElseThrow()
                    .ragRetryLater("unavailable", LocalDateTime.now().minusSeconds(1)));
            materialRagSync.run();
        }
        material = materialRepository.findById(materialId).orElseThrow();
        assertThat(material.getRagStatus()).isEqualTo(RagStatus.PENDING);

        // 켜지면 보낸다
        FakeAiService.respondWith(200);
        transactionTemplate.executeWithoutResult(status -> materialRepository.findById(materialId).orElseThrow()
                .ragRetryLater("unavailable", LocalDateTime.now().minusSeconds(1)));
        materialRagSync.run();
        assertThat(materialRepository.findById(materialId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.ACCEPTED);
    }

    @Test
    void 진행_중인_회의에서_질문하면_이_회의_자료와_같은_폴더_회의록으로_답하고_내_대화만_남긴다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long attendee = newId();
        Long viewer = newId();
        Long folderId = folder(workspaceId, "폴더", owner, attendee, viewer);
        Long meetingId = createMeeting(folderId, owner, owner, attendee);
        Long materialId = upload(meetingId, owner, "예산안.xlsx");
        FakeAiService.minutesDocs(folderId, "[301, 302]");
        String path = "/api/meetings/" + meetingId + "/chat";

        // 진행 중인 회의에서만, 참석자만 쓴다
        call(owner, post(path), query("예산 얼마야?"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_IN_PROGRESS"));
        call(owner, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        call(viewer, post(path), query("예산 얼마야?")).andExpect(status().isForbidden());

        // 실시간 요약을 받아 두면 질문할 때 summary[] 그대로 넘긴다 (챗봇 명세 v0.5)
        mockMvc.perform(put("/internal/meetings/" + meetingId + "/live-summary").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"0.1","meetingId":"%d","version":1,"atMs":134200,"final":false,
                                 "summary":[{"text":"버튼 색은 접근성 기준에 맞춰 다시 정하자는 의견이 나옴.","topic":"버튼 색",
                                             "evidence":[{"start":91.92,"end":117.6}]}]}""".formatted(meetingId)))
                .andExpect(status().isNoContent());

        FakeAiService.chatAnswer(200, ANSWERED);
        call(owner, post(path), query("  시안 언제까지 다시 보기로 했지?  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("answered"))
                .andExpect(jsonPath("$.data.question.role").value("USER"))
                .andExpect(jsonPath("$.data.question.text").value("시안 언제까지 다시 보기로 했지?"))
                .andExpect(jsonPath("$.data.answer.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.answer.basis").value("mixed"))
                .andExpect(jsonPath("$.data.answer.citations[0].location.label").value("안건1 안건의 결정"));

        JsonNode first = lastChat(meetingId);
        assertThat(first.path("workspaceId").asLong()).isEqualTo(workspaceId);
        assertThat(first.path("userId").asLong()).isEqualTo(owner);
        assertThat(first.path("userName").asString()).isEqualTo("user" + owner);
        assertThat(first.path("query").asString()).isEqualTo("시안 언제까지 다시 보기로 했지?");
        assertThat(first.path("history").size()).isZero();
        assertThat(first.path("liveSummary").size()).isEqualTo(1);
        assertThat(first.path("liveSummary").get(0).path("topic").asString()).isEqualTo("버튼 색");
        assertThat(first.path("liveSummary").get(0).path("evidence").get(0).path("start").asDouble()).isEqualTo(91.92);
        assertThat(pairs(first.path("docs")))
                .containsExactly("upload:" + materialId, "minutes:301", "minutes:302");

        // 이어지는 질문에는 앞의 대화를 넘긴다 (REQ-186)
        FakeAiService.chatAnswer(200, NO_RESULT);
        call(owner, post(path), query("그거 누가 맡았어?"))
                .andExpect(jsonPath("$.data.status").value("no_result"))
                .andExpect(jsonPath("$.data.answer.text").value("회의 자료에서 찾을 수 없어요"));
        JsonNode second = lastChat(meetingId);
        assertThat(second.path("history").size()).isEqualTo(2);
        assertThat(second.path("history").get(0).path("role").asString()).isEqualTo("user");
        assertThat(second.path("history").get(1).path("role").asString()).isEqualTo("assistant");

        // 실패한 질문은 남기지 않는다 (다시 물을 수 있게)
        FakeAiService.chatAnswer(200, "{\"status\":\"failed\",\"basis\":null,\"answer\":null,\"citations\":[],"
                + "\"errorCode\":\"llm_timeout\"}");
        call(owner, post(path), query("다시?"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.errorCode").value("llm_timeout"))
                .andExpect(jsonPath("$.data.question").doesNotExist());
        FakeAiService.chatAnswer(503, NO_RESULT);
        call(owner, post(path), query("다시?"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.errorCode").value("unavailable"));

        // 대화는 사용자마다 따로 본다
        call(owner, get(path), null)
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].text").value("시안 언제까지 다시 보기로 했지?"))
                .andExpect(jsonPath("$.data[3].status").value("no_result"));
        call(attendee, get(path), null).andExpect(jsonPath("$.data.length()").value(0));
        call(viewer, get(path), null).andExpect(status().isForbidden());
    }

    @Test
    void 질문은_천_자까지_받는다() throws Exception {
        Long owner = newId();
        Long folderId = folder(newId(), "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        call(owner, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());

        call(owner, post("/api/meetings/" + meetingId + "/chat"), query("가".repeat(1001)))
                .andExpect(status().isBadRequest());
        call(owner, post("/api/meetings/" + meetingId + "/chat"), query("   "))
                .andExpect(status().isBadRequest());
    }

    private Long upload(Long meetingId, Long userId, String fileName) throws Exception {
        String path = "/api/meetings/" + meetingId + "/materials";
        byte[] content = ("내용 " + fileName).getBytes(StandardCharsets.UTF_8);
        var prepared = call(userId, post(path),
                "{\"fileName\":\"%s\",\"contentType\":\"text/plain\",\"size\":%d}".formatted(fileName, content.length))
                .andExpect(status().isCreated());
        Long materialId = id(prepared, "$.data.materialId");
        httpClient.send(HttpRequest.newBuilder(URI.create(data(prepared, "$.data.uploadUrl")))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(), HttpResponse.BodyHandlers.discarding());
        call(userId, post(path + "/" + materialId + "/complete"), null).andExpect(status().isOk());
        return materialId;
    }

    private void callback(Long materialId, String docVersion, String status, String errorCode) throws Exception {
        String body = jsonMapper.writeValueAsString(java.util.Map.of("sourceType", "upload", "docId", materialId,
                "docVersion", docVersion, "status", status, "errorCode", errorCode == null ? "" : errorCode,
                "chunkCount", 0)).replace("\"errorCode\":\"\"", "\"errorCode\":null");
        mockMvc.perform(post("/internal/rag/ingest-result").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    private String etagOf(Long materialId) {
        return materialRepository.findById(materialId).orElseThrow().getEtag();
    }

    private List<JsonNode> ingests(Long materialId) {
        return FakeAiService.receivedRag("POST /internal/rag/documents").stream()
                .map(jsonMapper::readTree)
                .filter(body -> body.path("docId").asLong() == materialId)
                .toList();
    }

    private JsonNode lastIngest(Long materialId) {
        List<JsonNode> sent = ingests(materialId);
        assertThat(sent).isNotEmpty();
        return sent.getLast();
    }

    private JsonNode lastChat(Long meetingId) {
        List<JsonNode> sent = FakeAiService.receivedRag("POST /internal/rag/chat").stream()
                .map(jsonMapper::readTree)
                .filter(body -> body.path("meetingId").asLong() == meetingId)
                .toList();
        assertThat(sent).isNotEmpty();
        return sent.getLast();
    }

    private static List<String> pairs(JsonNode docs) {
        List<String> pairs = new java.util.ArrayList<>();
        docs.forEach(doc -> pairs.add(doc.path("sourceType").asString() + ":" + doc.path("docId").asLong()));
        return pairs;
    }

    private static String query(String text) {
        return "{\"query\":\"" + text + "\"}";
    }
}
