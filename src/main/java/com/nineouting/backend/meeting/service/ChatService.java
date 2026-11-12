package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.meeting.client.AiRejectedException;
import com.nineouting.backend.meeting.client.AiServiceException;
import com.nineouting.backend.meeting.client.MinutesDocsClient;
import com.nineouting.backend.meeting.client.RagClient;
import com.nineouting.backend.meeting.config.AiFeedProperties;
import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.AgendaState;
import com.nineouting.backend.meeting.domain.ChatMessage;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingMaterial;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.TranscriptLine;
import com.nineouting.backend.meeting.dto.ChatAnswerResponse;
import com.nineouting.backend.meeting.dto.ChatMessageResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import com.nineouting.backend.meeting.repository.ChatMessageRepository;
import com.nineouting.backend.meeting.repository.MeetingMaterialRepository;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * REQ-185, 186 회의 중 챗봇 (docs/services/rag-chatbot.md 4장, ai-service docs/api/rag-chat-contract.md).
 * 찾는 문서: 이 회의의 사전 자료 + 같은 폴더의 작성 완료 회의록. 진행 중인 회의는 안건 보드와 최근 전사 줄로 넘긴다.
 * 대화 기록은 사용자마다, 회의마다 따로 저장하고 질문할 때 최근 20개를 넘긴다. 실패한 질문은 저장하지 않는다.
 * 질문, 답, 회의 내용은 로그에 남기지 않는다 (챗봇 명세 7장).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int MAX_DOCS = 1000;
    static final int MAX_HISTORY = 20;
    static final int MAX_HISTORY_TEXT = 4000;
    static final int MAX_AGENDAS = 50;
    static final int MAX_LINES = 500;
    static final int MAX_LINE_CHARS = 30_000;
    /** 최근 전사 줄 범위 (챗봇 명세 3.1 권장 10분) */
    static final long RECENT_MS = 10 * 60 * 1000L;
    private static final String UNAVAILABLE = "unavailable";
    private static final String NO_RESULT_TEXT = "회의 자료에서 찾을 수 없어요";

    private final MeetingAccess access;
    private final ChatMessageRepository messageRepository;
    private final MeetingMaterialRepository materialRepository;
    private final AgendaRepository agendaRepository;
    private final AgendaStateRepository stateRepository;
    private final TranscriptLineRepository lineRepository;
    private final MinutesDocsClient minutesDocsClient;
    private final RagClient ragClient;
    private final UserClient userClient;
    private final AiFeedProperties aiProperties;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** 내 대화 (오래된 것부터). 회의 참석자만 */
    public List<ChatMessageResponse> history(Long meetingId, Long userId) {
        return transactionTemplate.execute(status -> {
            access.requireAttendee(meetingId, userId);
            return messageRepository.findByMeetingIdAndUserIdOrderById(meetingId, userId).stream()
                    .map(message -> ChatMessageResponse.of(message, jsonMapper))
                    .toList();
        });
    }

    /** 질문 하나. 진행 중인 회의의 참석자만. ai-service를 기다리는 동안(최대 60초) DB 연결을 잡지 않는다 */
    public ChatAnswerResponse ask(Long meetingId, Long userId, String query) {
        Prepared prepared = transactionTemplate.execute(status -> prepare(meetingId, userId, query.strip()));
        if (!aiProperties.enabled()) {
            return ChatAnswerResponse.failed(UNAVAILABLE);
        }
        JsonNode result;
        try {
            prepared.body().set("docs", docs(prepared));
            result = ragClient.chat(prepared.body());
        } catch (AiRejectedException e) {
            log.warn("ai-service가 챗봇 질문을 거절했습니다 (meetingId={}): {}", meetingId, e.getMessage());
            return ChatAnswerResponse.failed(UNAVAILABLE);
        } catch (AiServiceException e) {
            log.warn("챗봇 답을 받지 못했습니다 (meetingId={}): {}", meetingId, e.getMessage());
            return ChatAnswerResponse.failed(UNAVAILABLE);
        }
        String status = result == null ? "failed" : result.path("status").asString("failed");
        if (!"answered".equals(status) && !"no_result".equals(status)) {
            return ChatAnswerResponse.failed(result == null ? UNAVAILABLE : result.path("errorCode").asString(UNAVAILABLE));
        }
        String answer = result.path("answer").asString(NO_RESULT_TEXT);
        String basis = result.path("basis").isString() ? result.path("basis").asString() : null;
        String citations = jsonMapper.writeValueAsString(result.path("citations").isArray()
                ? result.path("citations") : jsonMapper.createArrayNode());
        return transactionTemplate.execute(tx -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ChatMessage question = messageRepository.save(ChatMessage.question(meetingId, userId, prepared.query(), now));
            ChatMessage saved = messageRepository.save(ChatMessage.answer(meetingId, userId, answer, status, basis,
                    citations, now));
            return new ChatAnswerResponse(status, null, ChatMessageResponse.of(question, jsonMapper),
                    ChatMessageResponse.of(saved, jsonMapper));
        });
    }

    private Prepared prepare(Long meetingId, Long userId, String query) {
        Meeting meeting = access.requireAttendee(meetingId, userId);
        if (!meeting.is(MeetingStatus.IN_PROGRESS)) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_IN_PROGRESS);
        }
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("workspaceId", meeting.getWorkspaceId());
        body.put("meetingId", meetingId);
        body.put("userId", userId);
        String userName = userName(userId, meeting.getWorkspaceId());
        if (userName != null) {
            body.put("userName", userName);
        }
        body.put("query", query);
        body.set("history", historyForAi(meetingId, userId));
        AgendaState state = stateRepository.findById(meetingId).orElse(null);
        Long currentAgendaId = state == null ? null : state.getCurrentAgendaId();
        if (currentAgendaId != null) {
            body.put("currentAgendaId", currentAgendaId);
        }
        body.set("liveAgendas", liveAgendas(meetingId, state, currentAgendaId));
        body.set("recentLines", recentLines(meetingId));
        List<Long> materialIds = materialRepository.findByMeetingIdAndStatusOrderById(meetingId,
                MeetingMaterial.Status.READY).stream().map(MeetingMaterial::getId).toList();
        return new Prepared(query, meeting.getFolderId(), materialIds, body);
    }

    /** 이 회의의 사전 자료를 먼저, 같은 폴더의 작성 완료 회의록은 최근 회의부터 1,000개까지 (챗봇 명세 3.1) */
    private ArrayNode docs(Prepared prepared) {
        ArrayNode docs = jsonMapper.createArrayNode();
        prepared.materialIds().stream().limit(MAX_DOCS)
                .forEach(id -> docs.addObject().put("sourceType", MaterialRagSync.SOURCE_TYPE).put("docId", id));
        minutesDocsClient.confirmedMinutesIds(prepared.folderId()).stream()
                .limit(Math.max(0, MAX_DOCS - docs.size()))
                .forEach(id -> docs.addObject().put("sourceType", "minutes").put("docId", id));
        return docs;
    }

    private ArrayNode historyForAi(Long meetingId, Long userId) {
        List<ChatMessage> recent = new ArrayList<>(messageRepository.findByMeetingIdAndUserIdOrderByIdDesc(meetingId, userId,
                PageRequest.of(0, MAX_HISTORY)));
        Collections.reverse(recent);
        ArrayNode history = jsonMapper.createArrayNode();
        recent.forEach(message -> history.addObject()
                .put("role", message.wireRole())
                .put("text", message.getText().length() > MAX_HISTORY_TEXT
                        ? message.getText().substring(0, MAX_HISTORY_TEXT) : message.getText()));
        return history;
    }

    /** 안건 보드의 모든 안건에 제목을 더한다. 50개를 넘으면 지금 안건을 먼저, 나머지는 안건 순서대로 */
    private ArrayNode liveAgendas(Long meetingId, AgendaState state, Long currentAgendaId) {
        ArrayNode result = jsonMapper.createArrayNode();
        if (state == null || state.getBoard() == null) {
            return result;
        }
        List<Agenda> agendas = agendaRepository.findByMeetingIdOrderByPosition(meetingId);
        Map<String, Agenda> byId = agendas.stream()
                .collect(Collectors.toMap(agenda -> agenda.getId().toString(), agenda -> agenda, (a, b) -> a, LinkedHashMap::new));
        Map<String, ObjectNode> boardAgendas = new LinkedHashMap<>();
        for (JsonNode node : jsonMapper.readTree(state.getBoard()).path("agendas")) {
            String id = node.path("agendaId").asString("");
            if (node.isObject() && byId.containsKey(id)) {
                ObjectNode agenda = ((ObjectNode) node).deepCopy();
                agenda.put("title", byId.get(id).getTitle());
                boardAgendas.put(id, agenda);
            }
        }
        List<ObjectNode> ordered = new ArrayList<>();
        String current = currentAgendaId == null ? null : currentAgendaId.toString();
        if (boardAgendas.size() > MAX_AGENDAS && current != null && boardAgendas.containsKey(current)) {
            ordered.add(boardAgendas.get(current));
        }
        byId.keySet().stream().filter(boardAgendas::containsKey)
                .filter(id -> ordered.isEmpty() || !id.equals(current))
                .map(boardAgendas::get)
                .forEach(ordered::add);
        ordered.stream().limit(MAX_AGENDAS).forEach(result::add);
        return result;
    }

    /** 최근 10분의 확정 전사 줄. 최신 줄부터 500줄, 30,000자까지 고르고 오래된 줄부터 담는다 */
    private ArrayNode recentLines(Long meetingId) {
        List<TranscriptLine> lines = lineRepository.findByMeetingIdAndDeletedFalseOrderByStartMsAscSeqAsc(meetingId).stream()
                .filter(TranscriptLine::isFinalLine)
                .toList();
        ArrayNode result = jsonMapper.createArrayNode();
        if (lines.isEmpty()) {
            return result;
        }
        long lastMs = lines.stream().mapToLong(line -> line.getEndMs() == null ? line.getStartMs() : line.getEndMs())
                .max().orElse(0);
        Deque<TranscriptLine> picked = new ArrayDeque<>();
        int chars = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            TranscriptLine line = lines.get(i);
            if (line.getStartMs() < lastMs - RECENT_MS || picked.size() >= MAX_LINES
                    || chars + line.getText().length() > MAX_LINE_CHARS) {
                break;
            }
            chars += line.getText().length();
            picked.addFirst(line);
        }
        picked.stream().sorted((a, b) -> Integer.compare(a.getSeq(), b.getSeq())).forEach(line -> {
            ObjectNode node = result.addObject()
                    .put("seq", line.getSeq())
                    .put("startMs", line.getStartMs())
                    .put("text", line.getText())
                    .put("lowConfidence", line.isLowConfidence());
            if (line.getEndMs() == null) {
                node.putNull("endMs");
            } else {
                node.put("endMs", line.getEndMs());
            }
            if (line.getKind() == null) {
                node.putNull("kind");
            } else {
                node.put("kind", line.getKind());
            }
        });
        return result;
    }

    /** 워크스페이스 프로필 이름 ("내 할 일" 같은 질문에 쓴다). 못 가져오면 넘기지 않는다 */
    private String userName(Long userId, Long workspaceId) {
        try {
            UserSummary user = userClient.findUsers(Set.of(userId), workspaceId).get(userId);
            return user == null ? null : user.name();
        } catch (RuntimeException e) {
            log.warn("챗봇 질문자 이름을 가져오지 못했습니다 (userId={}): {}", userId, e.getMessage());
            return null;
        }
    }

    private record Prepared(String query, Long folderId, List<Long> materialIds, ObjectNode body) {
    }
}
