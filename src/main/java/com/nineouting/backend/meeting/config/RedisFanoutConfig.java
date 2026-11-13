package com.nineouting.backend.meeting.config;

import com.nineouting.backend.meeting.service.RoomFanout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RedisFanoutConfig {

    /**
     * 모든 인스턴스가 회의방 채널을 구독한다.
     * 받은 메시지는 스레드 하나에서 받은 순서대로 보낸다. 기본값(메시지마다 새 스레드)은 같은 전사 줄의 갱신이
     * 거꾸로 화면에 갈 수 있다 (RoomFanoutOrderTest). 전송이 느린 브라우저는 MeetingRoomHub가 5초에서 끊는다.
     */
    @Bean
    RedisMessageListenerContainer roomFanoutListener(RedisConnectionFactory connectionFactory, RoomFanout fanout) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(inOrderExecutor());
        container.addMessageListener(fanout, new ChannelTopic(RoomFanout.CHANNEL));
        return container;
    }

    /** 빈으로 두지 않는다 (Executor 빈이 생기면 Spring Boot 기본 작업 실행기가 만들어지지 않는다) */
    private static ThreadPoolTaskExecutor inOrderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("room-fanout-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
