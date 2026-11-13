package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 회의방 메시지는 Redis에 올린 순서대로 받아야 한다 (같은 줄의 갱신이 거꾸로 그려지지 않게) */
class RoomFanoutOrderTest extends ApiTestSupport {

    private static final int COUNT = 300;

    @Autowired
    @Qualifier("roomFanoutListener")
    RedisMessageListenerContainer container;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 회의방_구독은_올린_순서대로_받는다() throws Exception {
        String channel = "test:order:" + UUID.randomUUID();
        List<String> received = new CopyOnWriteArrayList<>();
        // 실제 WebSocket 전송처럼 처리 시간이 메시지마다 조금씩 다르다
        container.addMessageListener((message, pattern) -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(3));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            received.add(new String(message.getBody(), StandardCharsets.UTF_8));
        }, new ChannelTopic(channel));
        // 구독이 붙을 때까지 시험 메시지를 보낸다
        long deadline = System.currentTimeMillis() + 10_000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            redis.convertAndSend(channel, "ready");
            Thread.sleep(50);
        }
        Thread.sleep(200);
        received.clear();

        for (int i = 0; i < COUNT; i++) {
            redis.convertAndSend(channel, Integer.toString(i));
        }
        deadline = System.currentTimeMillis() + 10_000;
        while (received.size() < COUNT && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertThat(received).containsExactlyElementsOf(IntStream.range(0, COUNT).mapToObj(Integer::toString).toList());
    }
}
