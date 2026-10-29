package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.AgendaDecisionPoint;
import com.nineouting.backend.meeting.domain.AgendaEdit;
import com.nineouting.backend.meeting.domain.AgendaEditKind;
import com.nineouting.backend.meeting.domain.AgendaState;
import com.nineouting.backend.meeting.domain.AgendaStatus;
import com.nineouting.backend.meeting.domain.LineAgenda;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.dto.AgendaBoardResponse;
import com.nineouting.backend.meeting.dto.AgendaItemEditRequest;
import com.nineouting.backend.meeting.dto.DraftSourceResponse;
import com.nineouting.backend.meeting.dto.LineAgendaResponse;
import com.nineouting.backend.meeting.dto.RoomMessage;
import com.nineouting.backend.meeting.dto.TodaySummaryResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.AgendaEditRepository;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import com.nineouting.backend.meeting.repository.LineAgendaRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 안건 보드 (REQ-085~097, 124~126). ai-service docs/api/agenda-*.md.
 * <ul>
 *   <li>ai-service가 보낸 줄별 분류·보드는 version이 클 때만 반영하고 커밋 뒤 회의방으로 보낸다</li>
 *   <li>보드는 JSON 그대로 저장하고, 안건 상태(REQ-086·088)와 새 안건 제안(30초 자동 무시), 회의가 끝난 뒤의
 *       오늘 회의 요약(REQ-097)만 따로 읽는다</li>
 *   <li>회의록 작성자의 고치기·무시·끝내기는 agenda_edits에 남기고 AiFeedJob(AgendaSync)이 ai-service로 보낸다.
 *       ai-service가 고친 결과를 반영한 보드를 다시 보낸다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaBoardService {

    /** 새 안건 제안 창은 30초 뒤 사라지며, 그때까지 등록·무시가 없으면 무시로 본다 (REQ-091, 2026-10-23 결정) */
    static final Duration OFF_TOPIC_AUTO_DISMISS = Duration.ofSeconds(30);

    private static final Set<String> TARGETS = Set.of("summary", "opinions", "todos");
    private static final Set<String> OPINION_KINDS = Set.of("OPINION", "EVIDENCE", "CONCERN", "QUESTION");

    private final AgendaStateRepository stateRepository;
    private final LineAgendaRepository lineAgendaRepository;
    private final AgendaEditRepository editRepository;
    private final AgendaRepository agendaRepository;
    private final MeetingRepository meetingRepository;
    private final TranscriptLineRepository transcriptLineRepository;
    private final MeetingAccess access;
    private final ApplicationEventPublisher events;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // ---- ai-service → 백엔드 ----

    /** 줄별 분류 (PUT /internal/meetings/{id}/agenda-classification). 바뀐 줄만 온다 */
    @Transactional
    public void receiveClassification(Long meetingId, JsonNode body) {
        requireSameMeeting(meetingId, body);
        access.requireStarted(meetingId);
        long version = requireVersion(body);
        AgendaState state = lockOrCreate(meetingId);
        Long currentAgendaId = toId(body.get("currentAgendaId"));
        if (!state.applyClassification(version, currentAgendaId)) {
            return;
        }
        List<JsonNode> lines = new ArrayList<>();
        body.path("lines").forEach(lines::add);
        Map<Integer, LineAgenda> existing = lineAgendaRepository.findByMeetingIdAndSeqIn(meetingId,
                        lines.stream().map(line -> line.path("seq").asInt()).toList()).stream()
                .collect(Collectors.toMap(LineAgenda::getSeq, Function.identity()));
        ArrayNode relayed = jsonMapper.createArrayNode();
        for (JsonNode line : lines) {
            int seq = line.path("seq").asInt();
            Long agendaId = toId(line.get("agendaId"));
            LineAgenda lineAgenda = existing.computeIfAbsent(seq, key -> new LineAgenda(meetingId, key));
            lineAgenda.classify(agendaId);
            lineAgendaRepository.save(lineAgenda);
            relayed.addObject().put("seq", seq).put("agendaId", agendaId);
        }
        ObjectNode message = jsonMapper.createObjectNode();
        message.put("version", version);
        message.put("currentAgendaId", currentAgendaId);
        message.put("final", body.path("final").asBoolean(false));
        message.set("lines", relayed);
        events.publishEvent(RoomEvent.send(meetingId, RoomMessage.agendaClassification(message)));
    }

    /** 안건 보드 (PUT /internal/meetings/{id}/agenda-board). 늘 전체가 온다 */
    @Transactional
    public void receiveBoard(Long meetingId, JsonNode body) {
        requireSameMeeting(meetingId, body);
        access.requireStarted(meetingId);
        long version = requireVersion(body);
        JsonNode offTopic = body.path("offTopic");
        String suggestDetectedAt = offTopic.isObject() && "SUGGEST".equals(offTopic.path("stage").asString(""))
                ? offTopic.path("detectedAt").asString("") : null;
        AgendaState state = lockOrCreate(meetingId);
        if (!state.applyBoard(version, jsonMapper.writeValueAsString(body), suggestDetectedAt, LocalDateTime.now(clock))) {
            return;
        }
        updateAgendaStatuses(meetingId, body.path("agendas"));
        JsonNode todaySummary = body.path("todaySummary");
        if (todaySummary.isObject() && todaySummary.path("final").asBoolean(false)) {
            state.keepFinalTodaySummary(jsonMapper.writeValueAsString(todaySummary));
        }
        events.publishEvent(RoomEvent.send(meetingId, RoomMessage.agendaBoard(body)));
    }

    // ---- 화면 ----

    @Transactional(readOnly = true)
    public AgendaBoardResponse board(Long meetingId, Long userId) {
        access.requireAttendee(meetingId, userId);
        return stateRepository.findById(meetingId)
                .map(state -> new AgendaBoardResponse(meetingId, state.getBoardVersion(), state.getCurrentAgendaId(),
                        state.getBoard() == null ? null : jsonMapper.readTree(state.getBoard()), state.getBoardReceivedAt()))
                .orElse(new AgendaBoardResponse(meetingId, 0, null, null, null));
    }

    @Transactional(readOnly = true)
    public List<LineAgendaResponse> lineAgendas(Long meetingId, Long userId) {
        access.requireAttendee(meetingId, userId);
        return lineAgendaRepository.findByMeetingIdOrderBySeq(meetingId).stream()
                .map(line -> new LineAgendaResponse(line.getSeq(), line.getAgendaId(), line.isCorrected()))
                .toList();
    }

    /** REQ-089 줄의 안건을 고친다. 회의록 작성자, 진행 중. 고친 줄은 그 뒤 자동 분류가 바꾸지 않는다 */
    @Transactional
    public LineAgendaResponse correctLine(Long meetingId, Long userId, int seq, Long agendaId) {
        access.requireWriterInProgress(meetingId, userId);
        transcriptLineRepository.findForUpdate(meetingId, seq)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (agendaId != null) {
            requireAgenda(meetingId, agendaId);
        }
        AgendaState state = lockOrCreate(meetingId);
        LineAgenda line = lineAgendaRepository.findById(new LineAgenda.Key(meetingId, seq))
                .orElseGet(() -> new LineAgenda(meetingId, seq));
        line.correct(agendaId);
        lineAgendaRepository.save(line);
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("seq", seq);
        payload.put("agendaId", agendaId);
        payload.put("rev", state.nextRev());
        saveEdit(meetingId, AgendaEditKind.CORRECTION, String.valueOf(seq), payload, true);
        ObjectNode relayed = jsonMapper.createObjectNode().put("seq", seq).put("agendaId", agendaId).put("corrected", true);
        events.publishEvent(RoomEvent.send(meetingId, RoomMessage.lineAgenda(relayed)));
        return new LineAgendaResponse(seq, agendaId, true);
    }

    /**
     * 요약 문장(REQ-085)·의견(REQ-087)·할 일(REQ-097)을 고친다. 회의록 작성자, 진행 중.
     * 고친 항목은 ai-service가 덮어쓰지 않고, 근거가 달라지면 "확인 필요"(needsCheck)를 켠다.
     */
    @Transactional
    public void editItem(Long meetingId, Long userId, String target, String itemId, AgendaItemEditRequest request) {
        access.requireWriterInProgress(meetingId, userId);
        requireTarget(target);
        requireAgenda(meetingId, request.agendaId());
        List<Integer> evidence = request.evidenceSeqs() == null ? List.of() : request.evidenceSeqs();
        AgendaState state = lockOrCreate(meetingId);
        ObjectNode payload = itemPayload(request.agendaId(), target, itemId, "edit");
        switch (target) {
            case "summary" -> {
                requireText(request.text());
                requireEvidence(evidence);
                payload.put("text", request.text());
            }
            case "opinions" -> {
                requireText(request.text());
                requireEvidence(evidence);
                if (request.kind() == null || !OPINION_KINDS.contains(request.kind())) {
                    throw new BusinessException(CommonErrorCode.INVALID_INPUT, "kind는 " + OPINION_KINDS + " 중 하나입니다");
                }
                payload.put("text", request.text());
                payload.put("kind", request.kind());
            }
            default -> {
                requireText(request.task());
                payload.put("task", request.task());
                payload.put("assigneeName", blankToNull(request.assigneeName()));
                payload.put("assigneeUserId", request.assigneeUserId());
                payload.put("dueText", blankToNull(request.dueText()));
            }
        }
        ArrayNode seqs = payload.putArray("evidenceSeqs");
        evidence.forEach(seqs::add);
        payload.put("rev", state.nextRev());
        saveEdit(meetingId, kindOf(target), "edit:" + request.agendaId() + ":" + target + ":" + itemId, payload, true);
    }

    /** "확인 필요" 표시를 확인함. ai-service가 재시작하면 확인 표시는 다시 보내지 않는다 */
    @Transactional
    public void confirmItem(Long meetingId, Long userId, String target, String itemId, Long agendaId) {
        access.requireWriterInProgress(meetingId, userId);
        requireTarget(target);
        requireAgenda(meetingId, agendaId);
        AgendaState state = lockOrCreate(meetingId);
        ObjectNode payload = itemPayload(agendaId, target, itemId, "confirm");
        payload.put("rev", state.nextRev());
        saveEdit(meetingId, kindOf(target), "confirm:" + agendaId + ":" + target + ":" + itemId, payload, false);
    }

    /** REQ-126 결정 내용을 고친다. 회의록 작성자, 진행 중. 오늘 회의 요약과 회의록에는 고친 내용을 쓴다 */
    @Transactional
    public void editDecision(Long meetingId, Long userId, Long agendaId, Long decisionPointId, String decisionText,
                             List<Integer> evidenceSeqs) {
        access.requireWriterInProgress(meetingId, userId);
        Agenda agenda = requireAgenda(meetingId, agendaId);
        if (agenda.getDecisionPoints().stream().noneMatch(point -> point.getId().equals(decisionPointId))) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        AgendaState state = lockOrCreate(meetingId);
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("agendaId", agendaId);
        payload.put("decisionPointId", decisionPointId);
        payload.put("decisionText", decisionText.trim());
        ArrayNode seqs = payload.putArray("evidenceSeqs");
        (evidenceSeqs == null ? List.<Integer>of() : evidenceSeqs).forEach(seqs::add);
        payload.put("rev", state.nextRev());
        saveEdit(meetingId, AgendaEditKind.DECISION, agendaId + ":" + decisionPointId, payload, true);
    }

    /** REQ-091 새 안건 제안 [무시], REQ-092 주제 이탈 알림 [확인]. 회의록 작성자, 진행 중 */
    @Transactional
    public void dismissOffTopic(Long meetingId, Long userId) {
        access.requireWriterInProgress(meetingId, userId);
        dismiss(lockOrCreate(meetingId));
    }

    /** REQ-091 제안 창이 뜨고 30초 안에 등록·무시가 없으면 무시로 보낸다 (여러 대가 돌아도 한 번만) */
    @Scheduled(fixedDelayString = "${nineouting.meeting.ai.off-topic-check-interval:5s}")
    public void dismissStaleOffTopics() {
        LocalDateTime before = LocalDateTime.now(clock).minus(OFF_TOPIC_AUTO_DISMISS);
        for (Long meetingId : stateRepository.findOffTopicSuggestedBefore(before)) {
            transactionTemplate.executeWithoutResult(status -> stateRepository.findForUpdate(meetingId)
                    .filter(state -> state.getOffTopicSuggestedAt() != null && !state.getOffTopicSuggestedAt().isAfter(before))
                    .ifPresent(state -> {
                        boolean inProgress = meetingRepository.findById(meetingId)
                                .map(meeting -> meeting.is(MeetingStatus.IN_PROGRESS)).orElse(false);
                        if (inProgress) {
                            dismiss(state);
                        } else {
                            state.offTopicHandled();
                        }
                    }));
        }
    }

    /** REQ-093 회의 끝내기. 결론 나지 않은 안건 목록(leftover)과 오늘 회의 요약(todaySummary)이 보드로 온다 */
    @Transactional
    public void requestEnd(Long meetingId, Long userId) {
        access.requireWriterInProgress(meetingId, userId);
        saveEndRequest(meetingId, "request");
    }

    /** REQ-095 이어서 논의하기. 보드의 leftover·todaySummary가 null로 돌아간다 */
    @Transactional
    public void cancelEnd(Long meetingId, Long userId) {
        access.requireWriterInProgress(meetingId, userId);
        boolean requested = editRepository.findByMeetingIdAndKindAndItemKey(meetingId, AgendaEditKind.END_REQUEST, "end")
                .map(edit -> "request".equals(jsonMapper.readTree(edit.getPayload()).path("action").asString("")))
                .orElse(false);
        if (!requested) {
            throw new BusinessException(MeetingErrorCode.END_NOT_REQUESTED);
        }
        saveEndRequest(meetingId, "cancel");
    }

    /** REQ-097 오늘 회의 요약. 회의가 끝났으면 저장해 둔 마지막 값(점검에서 고친 내용 포함), 아니면 지금 보드의 값 */
    @Transactional(readOnly = true)
    public TodaySummaryResponse todaySummary(Long meetingId, Long userId) {
        access.requireAttendee(meetingId, userId);
        return stateRepository.findById(meetingId).map(state -> {
            if (state.getTodaySummary() != null) {
                return new TodaySummaryResponse(true, jsonMapper.readTree(state.getTodaySummary()));
            }
            JsonNode current = state.getBoard() == null ? null : jsonMapper.readTree(state.getBoard()).get("todaySummary");
            return new TodaySummaryResponse(false, current == null || current.isNull() ? null : current);
        }).orElse(new TodaySummaryResponse(false, null));
    }

    /**
     * 점검(회의가 끝난 뒤)에서 고친 오늘 회의 요약을 저장한다 (ai-service는 회의가 끝나면 고친 것을 받지 않는다).
     * 회의록 작성자, 끝난 회의. {agendas[...]} 전체를 보낸다. AI 회의록 초안은 이 값으로 만든다
     */
    @Transactional
    public TodaySummaryResponse reviseTodaySummary(Long meetingId, Long userId, JsonNode todaySummary) {
        Meeting meeting = access.requireWriter(meetingId, userId);
        if (!meeting.is(MeetingStatus.FINISHED)) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_FINISHED);
        }
        if (todaySummary == null || !todaySummary.path("agendas").isArray()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "agendas 배열이 있어야 합니다");
        }
        lockOrCreate(meetingId).reviseTodaySummary(jsonMapper.writeValueAsString(todaySummary));
        return new TodaySummaryResponse(true, todaySummary);
    }

    /** minutes-service용: AI 회의록 초안의 재료 (안건, 점검을 마친 오늘 회의 요약) */
    @Transactional(readOnly = true)
    public DraftSourceResponse draftSource(Long meetingId) {
        meetingRepository.findById(meetingId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<DraftSourceResponse.Agenda> agendas = agendaRepository.findByMeetingIdOrderByPosition(meetingId).stream()
                .map(agenda -> {
                    List<AgendaDecisionPoint> points = agenda.getDecisionPoints();
                    return new DraftSourceResponse.Agenda(agenda.getId(), agenda.getTitle(), IntStream.range(0, points.size())
                            .mapToObj(index -> new DraftSourceResponse.DecisionPoint(points.get(index).getId(), index + 1,
                                    points.get(index).getContent()))
                            .toList());
                })
                .toList();
        String saved = stateRepository.findById(meetingId).map(AgendaState::getTodaySummary).orElse(null);
        JsonNode summary = saved == null ? jsonMapper.createObjectNode().set("agendas", jsonMapper.createArrayNode())
                : jsonMapper.readTree(saved);
        return new DraftSourceResponse(meetingId, saved != null, agendas, summary);
    }

    /** 회의 시작(REQ-076)·회의 중 안건 추가(REQ-090) 때 ai-service로 안건 목록을 다시 보내게 한다 */
    @Transactional
    public void agendasChanged(Long meetingId) {
        lockOrCreate(meetingId).markAgendasDirty();
    }

    // ---- 내부 ----

    private void dismiss(AgendaState state) {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("meetingId", state.getMeetingId().toString());
        payload.put("action", "dismiss");
        int rev = state.nextRev();
        payload.put("rev", rev);
        // ai-service가 재시작하면 이 시각으로 다시 보낸다 (회의 음성 시간). 줄이 없으면 0
        Long atMs = transcriptLineRepository.findLastMs(state.getMeetingId());
        payload.put("atMs", atMs == null ? 0 : atMs);
        saveEdit(state.getMeetingId(), AgendaEditKind.OFF_TOPIC, "dismiss:" + rev, payload, true);
        state.offTopicHandled();
    }

    private void saveEndRequest(Long meetingId, String action) {
        AgendaState state = lockOrCreate(meetingId);
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("meetingId", meetingId.toString());
        payload.put("action", action);
        payload.put("rev", state.nextRev());
        saveEdit(meetingId, AgendaEditKind.END_REQUEST, "end", payload, true);
    }

    private ObjectNode itemPayload(Long agendaId, String target, String itemId, String action) {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("agendaId", agendaId);
        if (!"summary".equals(target)) {
            payload.put("target", "opinions".equals(target) ? "opinion" : "todo");
        }
        payload.put("itemId", itemId);
        payload.put("action", action);
        return payload;
    }

    private void saveEdit(Long meetingId, AgendaEditKind kind, String key, ObjectNode payload, boolean replay) {
        String json = jsonMapper.writeValueAsString(payload);
        LocalDateTime now = LocalDateTime.now(clock);
        editRepository.findByMeetingIdAndKindAndItemKey(meetingId, kind, key).ifPresentOrElse(
                edit -> edit.replace(json, now),
                () -> editRepository.save(new AgendaEdit(meetingId, kind, key, json, replay, now)));
    }

    /** 보드의 안건 상태를 안건 표에도 둔다 (미결 안건 REQ-088, 회의 끝내기 REQ-093). 모르는 값은 건너뛴다 */
    private void updateAgendaStatuses(Long meetingId, JsonNode boardAgendas) {
        Map<Long, Agenda> agendas = agendaRepository.findByMeetingIdOrderByPosition(meetingId).stream()
                .collect(Collectors.toMap(Agenda::getId, Function.identity()));
        for (JsonNode node : boardAgendas) {
            Agenda agenda = agendas.get(toIdOrNull(node.get("agendaId")));
            String status = node.path("status").asString("");
            if (agenda == null) {
                continue;
            }
            try {
                agenda.changeStatus(AgendaStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                log.debug("모르는 안건 상태라 건너뜁니다 (meetingId={}, status={})", meetingId, status);
            }
        }
    }

    private AgendaState lockOrCreate(Long meetingId) {
        return stateRepository.findForUpdate(meetingId)
                .orElseGet(() -> stateRepository.saveAndFlush(new AgendaState(meetingId)));
    }

    private Agenda requireAgenda(Long meetingId, Long agendaId) {
        return agendaRepository.findById(agendaId)
                .filter(agenda -> agenda.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private static AgendaEditKind kindOf(String target) {
        return "summary".equals(target) ? AgendaEditKind.SUMMARY : AgendaEditKind.ITEM;
    }

    private static void requireTarget(String target) {
        if (!TARGETS.contains(target)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "내용을 입력해 주세요");
        }
    }

    private static void requireEvidence(List<Integer> evidence) {
        if (evidence.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "evidenceSeqs(근거 줄)를 보내 주세요");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long requireVersion(JsonNode body) {
        long version = body.path("version").asLong(0);
        if (version <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "version이 없습니다");
        }
        return version;
    }

    /** ai-service는 meetingId를 받은 타입 그대로 돌려준다 (백엔드는 문자열로 보낸다) */
    private static void requireSameMeeting(Long meetingId, JsonNode body) {
        JsonNode id = body.get("meetingId");
        if (id != null && !id.isNull() && !meetingId.toString().equals(id.asString())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "주소와 본문의 meetingId가 다릅니다");
        }
    }

    /** 숫자나 숫자 문자열. null이면 해당 없음 */
    private static Long toId(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return Long.parseLong(node.asString());
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "안건 번호가 숫자가 아닙니다: " + node);
        }
    }

    private static Long toIdOrNull(JsonNode node) {
        try {
            return toId(node);
        } catch (BusinessException e) {
            return null;
        }
    }
}
