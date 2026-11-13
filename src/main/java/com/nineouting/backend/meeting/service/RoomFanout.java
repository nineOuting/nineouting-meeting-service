package com.nineouting.backend.meeting.service;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의방 메시지를 모든 인스턴스로 나눠 보낸다 (Redis Pub/Sub).
 * 전사 줄을 저장한 인스턴스와 참석자가 회의방으로 붙어 있는 인스턴스가 다를 수 있어서, Redis 채널에 올리고
 * 모든 인스턴스가 받아 자기에게 붙어 있는 연결로만 보낸다. 놓친 줄은 다시 연결할 때 GET /transcripts로 채운다.
 * 받는 것은 스레드 하나가 순서대로 하고(RedisFanoutConfig), 보내기는 회의 번호로 나눈 스레드에 넘긴다(StripedSender).
 * 그래서 같은 회의는 순서가 지켜지고, 한 연결이 막혀도 다른 회의는 늦어지지 않는다.
 */
@Slf4j
@Component
public class RoomFanout implements MessageListener {

    public static final String CHANNEL = "nineouting:meeting:room";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final MeetingRoomHub hub;
    private final StripedSender sender;

    public RoomFanout(StringRedisTemplate redis, JsonMapper jsonMapper, MeetingRoomHub hub, StripedSender sender) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.hub = hub;
        this.sender = sender;
    }

    public void publish(RoomEvent event) {
        redis.convertAndSend(CHANNEL, jsonMapper.writeValueAsString(new Envelope(event.meetingId(),
                jsonMapper.writeValueAsString(event.message()), event.close())));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode envelope = jsonMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            long meetingId = envelope.path("meetingId").asLong();
            String json = envelope.path("message").asString();
            boolean close = envelope.path("close").asBoolean();
            sender.execute(meetingId, () -> {
                hub.sendJson(meetingId, json);
                if (close) {
                    hub.close(meetingId);
                }
            });
        } catch (JacksonException e) {
            log.warn("회의방 메시지 형식이 아니어서 버립니다: {}", e.getMessage());
        }
    }

    /** message는 이미 JSON으로 만든 RoomMessage */
    record Envelope(Long meetingId, String message, boolean close) {
    }
}
