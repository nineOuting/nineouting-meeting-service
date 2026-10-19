package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.dto.RoomMessage;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의방 WebSocket (REQ-072, 112): 참석자 화면에 전사 텍스트와 수정본을 실시간으로 보낸다.
 * 연결 권한은 RoomTicketInterceptor가 입장권으로 확인한다. 브라우저는 받기만 하고, 보내는 메시지는 무시한다.
 * 이 인스턴스에 연결된 참석자에게만 보낸다 — 여러 인스턴스로 늘릴 때는 docs/backlog.md "회의방 여러 인스턴스".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingRoomHub extends TextWebSocketHandler {

    /** 연결할 때 RoomTicketInterceptor가 넣는 속성 */
    public static final String MEETING_ID = "meetingId";
    /** 느린 브라우저 하나 때문에 다른 참석자 전송이 밀리지 않게 한다 */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final JsonMapper jsonMapper;
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    /** 지금 ai-service 상태. 회의방에 들어오면 한 번 보낸다. 아직 모르면 null */
    private volatile RoomMessage aiStatus;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long meetingId = (Long) session.getAttributes().get(MEETING_ID);
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT);
        rooms.computeIfAbsent(meetingId, id -> ConcurrentHashMap.newKeySet()).add(decorated);
        RoomMessage current = aiStatus;
        if (current != null) {
            sendTo(meetingId, decorated, new TextMessage(jsonMapper.writeValueAsString(current)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove((Long) session.getAttributes().get(MEETING_ID), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 화면 → 서버로 보낼 것은 없다 (수정·삭제는 REST API)
    }

    public void send(Long meetingId, RoomMessage message) {
        Set<WebSocketSession> sessions = rooms.get(meetingId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage text = new TextMessage(jsonMapper.writeValueAsString(message));
        for (WebSocketSession session : sessions) {
            sendTo(meetingId, session, text);
        }
    }

    /** ai-service 상태가 바뀌면 열려 있는 모든 회의방에 알린다 */
    public void changeAiStatus(boolean available) {
        aiStatus = RoomMessage.aiStatus(available);
        rooms.keySet().forEach(meetingId -> send(meetingId, aiStatus));
    }

    private void sendTo(Long meetingId, WebSocketSession session, TextMessage text) {
        try {
            session.sendMessage(text);
        } catch (IOException | IllegalStateException e) {
            log.debug("회의방 전송 실패, 연결을 정리합니다 (meetingId={}): {}", meetingId, e.getMessage());
            remove(meetingId, session);
        }
    }

    /** 회의가 마무리되면 연결을 모두 닫는다 */
    public void close(Long meetingId) {
        Set<WebSocketSession> sessions = rooms.remove(meetingId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("회의방 연결 닫기 실패 (meetingId={}): {}", meetingId, e.getMessage());
            }
        }
    }

    int connectionCount(Long meetingId) {
        return rooms.getOrDefault(meetingId, Set.of()).size();
    }

    private void remove(Long meetingId, WebSocketSession session) {
        rooms.computeIfPresent(meetingId, (id, sessions) -> {
            sessions.removeIf(stored -> stored == session || stored.getId().equals(session.getId()));
            return sessions.isEmpty() ? null : sessions;
        });
    }
}
