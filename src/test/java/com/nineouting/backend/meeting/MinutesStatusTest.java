package com.nineouting.backend.meeting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.common.event.EventEnvelope;
import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Kafka 토픽에 회의록 이벤트를 넣고 회의 목록에 "검토 중·확정"이 보이는지 확인한다 (REQ-046) */
class MinutesStatusTest extends ApiTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void 회의록_검토_요청과_확정이_회의_목록에_보인다() throws Exception {
        Long user = newId();
        Long folderId = folder(newId(), "기획", user);
        Long meetingId = createMeeting(folderId, user, user);

        publish(MinutesReviewRequested.TOPIC, MinutesReviewRequested.TYPE,
                new MinutesReviewRequested(1L, meetingId, 1L, "회의", user, List.of(2L)));
        awaitMinutesStatus(user, meetingId, "REVIEWING");
        call(user, get("/api/folders/" + folderId + "/meetings"), null)
                .andExpect(jsonPath("$.data.items[0].minutesStatus").value("REVIEWING"));

        publish(MinutesConfirmed.TOPIC, MinutesConfirmed.TYPE,
                new MinutesConfirmed(1L, meetingId, 1L, folderId, "회의", List.of(user), LocalDateTime.now()));
        awaitMinutesStatus(user, meetingId, "CONFIRMED");
        call(user, get("/api/meetings/calendar").param("from", "2026-10-01").param("to", "2026-10-31"), null)
                .andExpect(jsonPath("$.data[0].minutesStatus").value("CONFIRMED"));
    }

    private void publish(String topic, String type, Object payload) throws Exception {
        kafkaTemplate.send(topic, jsonMapper.writeValueAsString(EventEnvelope.of(type, "test", null, payload))).get();
    }

    private void awaitMinutesStatus(Long userId, Long meetingId, String expected) throws Exception {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            String body = call(userId, get("/api/meetings/" + meetingId), null).andReturn().getResponse().getContentAsString();
            if (Objects.equals(JsonPath.read(body, "$.data.minutesStatus"), expected)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(WAIT + " 안에 회의록 상태가 " + expected + "가 되지 않았습니다");
    }
}
