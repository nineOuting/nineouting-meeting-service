package com.nineouting.backend.meeting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.event.EventEnvelope;
import com.nineouting.backend.common.event.workspace.FolderDeleted;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Kafka 토픽에 FolderDeleted를 넣고 inbox → 처리기 → 삭제까지 확인한다 (REQ-058) */
class FolderDeletedTest extends ApiTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void 폴더가_지워지면_그_안의_회의와_남은_안건을_지운다() throws Exception {
        Long workspaceId = newId();
        Long user = newId();
        Long deleted = folder(workspaceId, "지울 폴더", user);
        Long kept = folder(workspaceId, "남길 폴더", user);
        Long deletedMeeting = createMeeting(deleted, user, user);
        Long keptMeeting = createMeeting(kept, user, user);
        call(user, post("/api/meetings/" + deletedMeeting + "/start"), null);
        Long agendaId = id(call(user, get("/api/meetings/" + deletedMeeting), null), "$.data.agendas[0].agendaId");
        call(user, post("/api/meetings/" + deletedMeeting + "/carry-over"), "{\"agendaIds\":[%d]}".formatted(agendaId));

        kafkaTemplate.send(FolderDeleted.TOPIC, jsonMapper.writeValueAsString(EventEnvelope.of(FolderDeleted.TYPE,
                "test", null, new FolderDeleted(workspaceId, List.of(deleted))))).get();

        await(deletedMeeting, user);
        call(user, get("/api/folders/" + deleted + "/pending-agendas"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
        call(user, get("/api/meetings/" + keptMeeting), null).andExpect(status().isOk());
    }

    private void await(Long meetingId, Long userId) throws Exception {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            int status = call(userId, get("/api/meetings/" + meetingId), null).andReturn().getResponse().getStatus();
            if (status == 404) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(WAIT + " 안에 회의가 지워지지 않았습니다");
    }
}
