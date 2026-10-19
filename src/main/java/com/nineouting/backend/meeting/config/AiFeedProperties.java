package com.nineouting.backend.meeting.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회의 중 ai-service로 회의 알림·줄 묶음을 밀어 주기 (docs/services/ai-integration.md 2.4).
 *
 * @param baseUrl        ai-service 주소. 비우면 보내지 않는다 (ai-service가 없는 로컬·통합 테스트)
 * @param batchInterval  줄 묶음을 보내는 주기, ai-service가 실패할 때 다시 시도하는 간격
 * @param connectTimeout ai-service 연결 타임아웃
 * @param readTimeout    ai-service 응답 타임아웃
 * @param finishedGrace  회의가 끝난 뒤 늦게 오는 전사 줄을 기다리는 시간. 지나고 보낼 것이 없으면 정리한다
 */
@ConfigurationProperties(prefix = "nineouting.meeting.ai")
public record AiFeedProperties(String baseUrl, Duration batchInterval, Duration connectTimeout, Duration readTimeout,
                               Duration finishedGrace) {

    public boolean enabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
