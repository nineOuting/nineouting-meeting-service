package com.nineouting.backend.meeting.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REQ-185~187 챗봇(RAG). ai-service 주소는 회의 중 기능과 같은 nineouting.meeting.ai.base-url을 쓴다 (비우면 꺼짐).
 *
 * @param callbackBaseUrl   ai-service가 넣기 결과를 알려 줄 이 서비스 주소 (서비스 간 주소)
 * @param minutesServiceUrl 같은 폴더의 작성 완료 회의록 목록을 물을 minutes-service 주소
 * @param chatTimeout       챗봇 답을 기다리는 시간. ai-service는 60초 안에 답한다 (회의 중 챗봇 명세 1장)
 * @param sendInterval      검색용 저장소에 보낼 자료를 찾는 주기
 * @param maxAttempts       다시 보내는 횟수 상한. 넘으면 실패로 둔다
 * @param retryDelay        처음 다시 보낼 때 기다리는 시간. 실패할 때마다 두 배
 * @param acceptedTimeout   접수 뒤 이 시간 안에 콜백이 없으면 상태 조회로 확인한다
 */
@ConfigurationProperties(prefix = "nineouting.meeting.rag")
public record RagProperties(String callbackBaseUrl, String minutesServiceUrl, Duration chatTimeout,
                            Duration sendInterval, int maxAttempts, Duration retryDelay, Duration acceptedTimeout) {
}
