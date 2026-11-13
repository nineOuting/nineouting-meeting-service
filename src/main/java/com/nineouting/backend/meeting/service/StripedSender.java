package com.nineouting.backend.meeting.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 회의방 메시지를 회의 번호로 나눈 스레드 여러 개에서 보낸다.
 * 같은 회의는 늘 같은 스레드라 받은 순서대로 가고, 한 연결이 막혀도(브라우저 네트워크가 멈춤) 그 묶음의 회의만 늦어진다.
 * Executor를 구현하지 않는다 (Executor 빈이 생기면 Spring Boot 기본 작업 실행기가 만들어지지 않는다).
 */
@Slf4j
@Component
public class StripedSender {

    static final int STRIPES = 16;

    private final ThreadPoolTaskExecutor[] stripes = new ThreadPoolTaskExecutor[STRIPES];

    public StripedSender() {
        for (int i = 0; i < STRIPES; i++) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("room-send-" + i + "-");
            executor.setDaemon(true);
            executor.initialize();
            stripes[i] = executor;
        }
    }

    /** key(회의 번호)가 같은 일은 넣은 순서대로 한다 */
    public void execute(long key, Runnable task) {
        stripes[Math.floorMod(Long.hashCode(key), STRIPES)].execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("회의방 전송 실패 (meetingId={}): {}", key, e.getMessage());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        for (ThreadPoolTaskExecutor executor : stripes) {
            executor.shutdown();
        }
    }
}
