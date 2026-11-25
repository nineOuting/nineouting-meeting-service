package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.AgendaState;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.dto.MyTodoResponse;
import com.nineouting.backend.meeting.repository.AgendaStateRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.TodoCheckRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-196 홈 내 할 일. 할 일은 ai-service 안건 보드(agenda_states.board)에 있고, 완료 체크는 회의 중 할 일 체크(todo_checks)와 같다.
 * 홈에서 완료하면 회의 화면의 할 일에도 완료로 보인다
 */
@Service
@RequiredArgsConstructor
public class MyTodoService {

    /** 최근 끝난 회의 몇 개까지 볼지 */
    private static final int RECENT_MEETINGS = 50;

    private final MeetingRepository meetingRepository;
    private final AgendaStateRepository agendaStateRepository;
    private final TodoCheckRepository todoCheckRepository;
    private final WorkspaceClient workspaceClient;
    private final MeetingAccess access;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** 최근 회의부터. 지금 들어갈 수 없는 폴더의 회의는 뺀다 */
    // 다른 서비스 응답을 기다리는 동안 DB 연결을 잡고 있지 않는다. 조회마다 짧은 읽기 트랜잭션을 쓴다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MyTodoResponse> list(Long userId) {
        List<Meeting> meetings = meetingRepository.findAttended(userId, MeetingStatus.FINISHED,
                PageRequest.of(0, RECENT_MEETINGS));
        if (meetings.isEmpty()) {
            return List.of();
        }
        Map<Long, AccessibleFolder> folders = workspaceClient.accessibleFolders(userId).stream()
                .collect(Collectors.toMap(AccessibleFolder::folderId, Function.identity(), (a, b) -> a));
        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
        Map<Long, String> boards = agendaStateRepository.findAllById(meetingIds).stream()
                .filter(state -> state.getBoard() != null)
                .collect(Collectors.toMap(AgendaState::getMeetingId, AgendaState::getBoard));
        Set<String> checked = todoCheckRepository.findByMeetingIdIn(meetingIds).stream()
                .map(check -> check.getMeetingId() + ":" + check.getItemId())
                .collect(Collectors.toSet());

        List<MyTodoResponse> todos = new ArrayList<>();
        for (Meeting meeting : meetings) {
            AccessibleFolder folder = folders.get(meeting.getFolderId());
            String board = boards.get(meeting.getId());
            if (folder == null || board == null) {
                continue;
            }
            for (JsonNode todo : myTodos(board, userId)) {
                String itemId = todo.path("id").asString("");
                if (itemId.isEmpty() || checked.contains(meeting.getId() + ":" + itemId)) {
                    continue;
                }
                JsonNode due = todo.path("dueText");
                todos.add(new MyTodoResponse(meeting.getId(), itemId, todo.path("task").asString(""),
                        due.isString() ? due.asString() : null, meeting.getTitle(), meeting.getStartAt(),
                        meeting.getWorkspaceId(), meeting.getFolderId(), folder.name()));
            }
        }
        return todos;
    }

    /** 나에게 배정된 할 일만 완료할 수 있다 */
    @Transactional
    public void complete(Long meetingId, String itemId, Long userId) {
        access.requireAttendee(meetingId, userId);
        String board = agendaStateRepository.findById(meetingId).map(AgendaState::getBoard).orElse(null);
        boolean mine = board != null && myTodos(board, userId).stream()
                .anyMatch(todo -> itemId.equals(todo.path("id").asString("")));
        if (!mine) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        todoCheckRepository.insertIgnoringDuplicate(meetingId, itemId, userId, LocalDateTime.now(clock));
    }

    private List<JsonNode> myTodos(String board, Long userId) {
        List<JsonNode> todos = new ArrayList<>();
        for (JsonNode agenda : jsonMapper.readTree(board).path("agendas")) {
            for (JsonNode todo : agenda.path("todos")) {
                JsonNode assignee = todo.path("assigneeUserId");
                if (assignee.isNumber() && assignee.asLong() == userId) {
                    todos.add(todo);
                }
            }
        }
        return todos;
    }
}
