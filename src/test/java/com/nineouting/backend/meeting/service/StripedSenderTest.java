package com.nineouting.backend.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 회의방 전송: 같은 회의는 넣은 순서대로, 한 회의가 막혀도 다른 회의는 바로 간다 */
class StripedSenderTest {

    private final StripedSender sender = new StripedSender();

    @AfterEach
    void tearDown() {
        sender.shutdown();
    }

    @Test
    void 같은_회의는_넣은_순서대로_보낸다() throws Exception {
        List<Integer> sent = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(500);
        for (int i = 0; i < 500; i++) {
            int n = i;
            sender.execute(42L, () -> {
                sent.add(n);
                done.countDown();
            });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sent).containsExactlyElementsOf(IntStream.range(0, 500).boxed().toList());
    }

    @Test
    void 한_회의의_연결이_막혀도_다른_회의는_기다리지_않는다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch other = new CountDownLatch(1);
        long blocked = 1L;
        long free = 2L;   // 1과 다른 묶음
        sender.execute(blocked, () -> {
            try {
                release.await(10, TimeUnit.SECONDS);   // 막힌 브라우저로 보내는 중
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        sender.execute(free, other::countDown);

        assertThat(other.await(1, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void 보내다_실패해도_그_회의의_다음_메시지는_간다() throws Exception {
        CountDownLatch next = new CountDownLatch(1);
        sender.execute(7L, () -> {
            throw new IllegalStateException("연결이 닫힘");
        });
        sender.execute(7L, next::countDown);
        assertThat(next.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
