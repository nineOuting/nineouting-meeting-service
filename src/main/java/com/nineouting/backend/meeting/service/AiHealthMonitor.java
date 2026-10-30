package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.client.AiServiceClient;
import com.nineouting.backend.meeting.config.AiFeedProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ai-service 장애 감지 (REQ-082 "AI 일시 중단" 표시). /health를 주기적으로 보고,
 * 연달아 실패하면 멈춤, 한 번 성공하면 돌아옴으로 바꿔 회의방에 알린다.
 * 밀린 회의 알림·줄 묶음은 AiFeedJob이 복구 뒤 한꺼번에 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiHealthMonitor {

    /** 일시적인 지연 한 번으로 화면이 깜빡이지 않게 연달아 실패해야 멈춤으로 본다 */
    static final int FAILURES_TO_DOWN = 2;

    private final AiServiceClient aiServiceClient;
    private final AiFeedProperties properties;
    private final MeetingRoomHub hub;

    private Boolean available;
    private int consecutiveFailures;

    @Scheduled(fixedDelayString = "${nineouting.meeting.ai.health-interval:5s}")
    public synchronized void check() {
        if (!properties.enabled()) {
            return;
        }
        if (aiServiceClient.isHealthy()) {
            consecutiveFailures = 0;
            change(true);
        } else if (++consecutiveFailures >= FAILURES_TO_DOWN) {
            change(false);
        }
    }

    private void change(boolean nowAvailable) {
        if (available != null && available == nowAvailable) {
            return;
        }
        if (available != null) {
            log.warn(nowAvailable ? "ai-service가 돌아왔습니다" : "ai-service가 응답하지 않습니다 (AI 일시 중단)");
        }
        available = nowAvailable;
        hub.changeAiStatus(nowAvailable);
    }
}
