package com.nineouting.backend.meeting.config;

import com.nineouting.backend.meeting.service.MeetingRoomHub;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 회의방 WebSocket. 브라우저는 Gateway의 /ws/meetings/{meetingId}로 들어온다 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MeetingRoomWebSocketConfig implements WebSocketConfigurer {

    private final MeetingRoomHub hub;
    private final RoomTicketInterceptor ticketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 연결 권한은 1회용 입장권(인증된 API로만 받음)으로 확인하므로 출처(Origin)는 따로 막지 않는다
        registry.addHandler(hub, "/ws/meetings/*")
                .addInterceptors(ticketInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
