package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.meeting.client.AiRejectedException;
import com.nineouting.backend.meeting.client.AiServiceClient;
import com.nineouting.backend.meeting.domain.Agenda;
import com.nineouting.backend.meeting.domain.AgendaDecisionPoint;
import com.nineouting.backend.meeting.domain.AgendaEdit;
import com.nineouting.backend.meeting.domain.AgendaEditKind;
import com.nineouting.backend.meeting.domain.AgendaState;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingAttendee;
import com.nineouting.backend.meeting.repository.AgendaEditRepository;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 안건 목록과 사람의 고치기·무시·끝내기를 ai-service로 보낸다 (ai-service docs/api/agenda-*.md). AiFeedJob이 회의를
 * 잠근 채 줄 묶음보다 먼저 부른다. ai-service가 응답하지 않으면 AiServiceException이 나가 다음 주기에 다시 보낸다.
 * ai-service가 재시작하면(줄 묶음 1번을 다시 달라고 하면) 안건 목록과 다시 보낼 요청을 모두 다시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendaSync {

    private final AgendaStateRepository stateRepository;
    private final AgendaEditRepository editRepository;
    private final AgendaRepository agendaRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final UserClient userClient;
    private final AiServiceClient aiServiceClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** 안건 목록 → 줄 분류 고치기 → 요약 → 의견·할 일 → 결정 내용 → 제안 무시 → 끝내기 순서로 보낸다 */
    public void sendPending(Long meetingId) {
        Optional<AgendaState> found = stateRepository.findForUpdate(meetingId);
        if (found.isEmpty()) {
            return;
        }
        AgendaState state = found.get();
        if (state.isAgendasDirty()) {
            sendAgendas(state);
        }
        List<AgendaEdit> pending = editRepository.findByMeetingIdAndSentFalseOrderById(meetingId);
        for (AgendaEditKind kind : AgendaEditKind.values()) {
            List<AgendaEdit> edits = pending.stream().filter(edit -> edit.getKind() == kind).toList();
            if (edits.isEmpty()) {
                continue;
            }
            if (kind.arrayName() != null) {
                send(meetingId, kind, edits, batchBody(meetingId, kind, edits));
            } else {
                for (AgendaEdit edit : edits) {
                    send(meetingId, kind, List.of(edit), singleBody(edit));
                }
            }
        }
    }

    /** ai-service가 재시작해 회의 상태를 잃었다. 줄 묶음을 다시 보내기 전에 안건 목록과 고친 것을 모두 다시 보낸다 */
    public void markAllForResend(Long meetingId) {
        stateRepository.findForUpdate(meetingId).ifPresent(AgendaState::markAgendasDirty);
        editRepository.findByMeetingIdAndReplayTrue(meetingId).forEach(AgendaEdit::markForResend);
    }

    private void sendAgendas(AgendaState state) {
        Long meetingId = state.getMeetingId();
        Optional<Meeting> meeting = meetingRepository.findById(meetingId);
        if (meeting.isEmpty()) {
            state.agendasSent();
            return;
        }
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("meetingId", meetingId.toString());
        body.put("version", state.nextAgendasVersion(clock.millis()));
        ArrayNode agendas = body.putArray("agendas");
        List<Agenda> list = agendaRepository.findByMeetingIdOrderByPosition(meetingId);
        IntStream.range(0, list.size()).forEach(index -> agendas.add(agenda(list.get(index), index + 1)));
        body.set("attendees", attendees(meeting.get()));
        try {
            aiServiceClient.putAgendas(meetingId, jsonMapper.writeValueAsString(body));
        } catch (AiRejectedException e) {
            log.error("ai-service가 안건 목록을 거절해 버립니다 (meetingId={}): {}", meetingId, e.getMessage());
        }
        state.agendasSent();
    }

    private ObjectNode agenda(Agenda agenda, int position) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("agendaId", agenda.getId());
        node.put("position", position);
        node.put("title", agenda.getTitle());
        node.put("addedDuringMeeting", agenda.isAddedDuringMeeting());
        ArrayNode points = node.putArray("decisionPoints");
        List<AgendaDecisionPoint> decisionPoints = agenda.getDecisionPoints();
        for (int index = 0; index < decisionPoints.size(); index++) {
            ObjectNode point = points.addObject();
            point.put("decisionPointId", decisionPoints.get(index).getId());
            point.put("position", index + 1);
            point.put("text", decisionPoints.get(index).getContent());
        }
        return node;
    }

    /** 할 일 담당자 이름 맞추기에 쓴다. 워크스페이스에서 보이는 이름으로 (REQ-114) */
    private ArrayNode attendees(Meeting meeting) {
        List<Long> ids = attendeeRepository.findByMeetingIdOrderById(meeting.getId()).stream()
                .map(MeetingAttendee::getUserId).toList();
        Map<Long, UserSummary> users = ids.isEmpty() ? Map.of() : userClient.findUsers(ids, meeting.getWorkspaceId());
        ArrayNode attendees = jsonMapper.createArrayNode();
        for (Long id : ids) {
            UserSummary user = users.get(id);
            if (user != null) {
                attendees.addObject().put("userId", id).put("name", user.name());
            }
        }
        return attendees;
    }

    private String batchBody(Long meetingId, AgendaEditKind kind, List<AgendaEdit> edits) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetingId", meetingId.toString());
        List<JsonNode> items = new ArrayList<>();
        edits.forEach(edit -> items.add(jsonMapper.readTree(edit.getPayload())));
        body.put(kind.arrayName(), items);
        return jsonMapper.writeValueAsString(body);
    }

    /** 새 안건 제안 무시의 atMs는 재시작 복구로 다시 보낼 때만 담는다 (평소에는 ai-service가 받은 시점으로 처리) */
    private String singleBody(AgendaEdit edit) {
        ObjectNode body = (ObjectNode) jsonMapper.readTree(edit.getPayload());
        if (edit.getKind() == AgendaEditKind.OFF_TOPIC && !edit.isResend()) {
            body.remove("atMs");
        }
        return jsonMapper.writeValueAsString(body);
    }

    private void send(Long meetingId, AgendaEditKind kind, List<AgendaEdit> edits, String body) {
        try {
            aiServiceClient.postAgendaRequest(meetingId, kind.path(), body);
        } catch (AiRejectedException e) {
            log.error("ai-service가 {}를 거절해 버립니다 (meetingId={}, {}개): {}", kind.path(), meetingId, edits.size(),
                    e.getMessage());
        }
        edits.forEach(AgendaEdit::markSent);
    }
}
