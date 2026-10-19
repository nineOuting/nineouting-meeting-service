package com.nineouting.backend.meeting.config;

import com.nineouting.backend.meeting.service.RoomFanout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisFanoutConfig {

    /** 모든 인스턴스가 회의방 채널을 구독한다 */
    @Bean
    RedisMessageListenerContainer roomFanoutListener(RedisConnectionFactory connectionFactory, RoomFanout fanout) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(fanout, new ChannelTopic(RoomFanout.CHANNEL));
        return container;
    }
}
