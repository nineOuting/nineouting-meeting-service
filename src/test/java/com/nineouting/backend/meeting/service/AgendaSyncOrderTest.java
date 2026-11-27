package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.meeting.client.AiServiceClient;
import com.nineouting.backend.meeting.domain.AgendaEdit;
import com.nineouting.backend.meeting.domain.AgendaEditKind;
import com.nineouting.backend.meeting.domain.AgendaState;
import com.nineouting.backend.meeting.repository.AgendaEditRepository;
import com.nineouting.backend.meeting.repository.AgendaRepository;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import com.nineouting.backend.meeting.repository.MeetingAttendeeRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 결정 내용과 사항 상태는 종류 순서가 아니라 사용자가 한 순서(rev)대로 보낸다 */
class AgendaSyncOrderTest {

    private static final Long MEETING_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 10, 0);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AgendaStateRepository states = mock(AgendaStateRepository.class);
    private final AgendaEditRepository edits = mock(AgendaEditRepository.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AgendaSync sync = new AgendaSync(states, edits, mock(AgendaRepository.class),
            mock(MeetingRepository.class), mock(MeetingAttendeeRepository.class), mock(UserClient.class),
            aiServiceClient, jsonMapper, Clock.systemUTC());
    /** 보낸 요청: "주소 rev,rev" */
    private final List<String> sent = new ArrayList<>();

    @Test
    void AI에게_맡긴_뒤_고친_결정_내용은_상태_다음에_보낸다() {
        // 결정 내용 줄이 먼저 만들어져(id가 작아) 있어도 나중에 고쳤으면 나중에 간다
        pending(decision("7:70", 5), status("7:70", "AUTO", 4));

        sync.sendPending(MEETING_ID);

        assertThat(sent).containsExactly("status-edits 4", "decision-edits 5");
    }

    @Test
    void 고친_뒤_AI에게_맡기면_결정_내용_다음에_상태를_보낸다() {
        pending(decision("7:70", 1), status("7:70", "AUTO", 2));

        sync.sendPending(MEETING_ID);

        assertThat(sent).containsExactly("decision-edits 1", "status-edits 2");
    }

    @Test
    void 번갈아_쌓이면_이어지는_같은_종류끼리_묶어_차례로_보내고_다른_종류의_자리는_그대로다() {
        pending(edit(AgendaEditKind.OFF_TOPIC, "dismiss:1", 1),
                decision("7:70", 5),
                status("7:70", "DECIDED", 3),
                status("7:71", "UNDECIDED", 4),
                status("7:72", "AUTO", 6),
                edit(AgendaEditKind.ITEM, "edit:7:todo:k1-1", 2));

        sync.sendPending(MEETING_ID);

        assertThat(sent).containsExactly(
                "agenda-item-edits 2",
                "status-edits 3,4",
                "decision-edits 5",
                "status-edits 6",
                "off-topic-actions 1");
    }

    private void pending(AgendaEdit... list) {
        when(states.findForUpdate(MEETING_ID)).thenReturn(Optional.of(new AgendaState(MEETING_ID)));
        when(edits.findByMeetingIdAndSentFalseOrderById(MEETING_ID)).thenReturn(List.of(list));
        doAnswer(invocation -> {
            JsonNode body = jsonMapper.readTree(invocation.<String>getArgument(2));
            JsonNode items = body.has("edits") ? body.get("edits") : jsonMapper.createArrayNode().add(body);
            List<String> revs = new ArrayList<>();
            items.forEach(item -> revs.add(item.path("rev").asString()));
            sent.add(invocation.getArgument(1) + " " + String.join(",", revs));
            return null;
        }).when(aiServiceClient).postAgendaRequest(eq(MEETING_ID), anyString(), anyString());
    }

    private AgendaEdit decision(String key, int rev) {
        return new AgendaEdit(MEETING_ID, AgendaEditKind.DECISION, key,
                "{\"decisionText\":\"고친 글\",\"evidenceSeqs\":[],\"rev\":" + rev + "}", true, NOW);
    }

    private AgendaEdit status(String key, String status, int rev) {
        return new AgendaEdit(MEETING_ID, AgendaEditKind.STATUS, key,
                "{\"status\":\"" + status + "\",\"rev\":" + rev + "}", true, NOW);
    }

    private AgendaEdit edit(AgendaEditKind kind, String key, int rev) {
        return new AgendaEdit(MEETING_ID, kind, key, "{\"rev\":" + rev + "}", true, NOW);
    }
}
