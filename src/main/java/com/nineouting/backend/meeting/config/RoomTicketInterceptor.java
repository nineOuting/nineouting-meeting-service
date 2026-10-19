package com.nineouting.backend.meeting.config;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.meeting.service.ConnectionTicketService;
import com.nineouting.backend.meeting.service.MeetingRoomHub;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 회의방 연결(/ws/meetings/{meetingId}?ticket=...)을 받을 때 1회용 입장권을 확인한다.
 * 브라우저 WebSocket은 Authorization 헤더를 붙일 수 없어서 JWT 대신 입장권을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class RoomTicketInterceptor implements HandshakeInterceptor {

    private static final String USER_ID = "userId";

    private final ConnectionTicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            String path = request.getURI().getPath();
            Long meetingId = Long.valueOf(path.substring(path.lastIndexOf('/') + 1));
            String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
            if (ticket == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(USER_ID, ticketService.consumeRoom(ticket, meetingId));
            attributes.put(MeetingRoomHub.MEETING_ID, meetingId);
            return true;
        } catch (NumberFormatException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        } catch (BusinessException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // 확인은 beforeHandshake에서 끝난다
    }
}
