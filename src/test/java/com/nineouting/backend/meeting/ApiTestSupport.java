package com.nineouting.backend.meeting;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.FolderAccess;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.outbox.OutboxEventRepository;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 PostgreSQL·Kafka와 security-common 토큰 검증을 거치는 API 테스트의 공통 도우미.
 * user-service·workspace-service는 띄우지 않고 가짜로 바꾼다.
 * <ul>
 *   <li>사용자 이름: user{ID}</li>
 *   <li>폴더: {@link #folder}로 만든 폴더와 그 폴더에 들어갈 수 있는 사람</li>
 * </ul>
 */
// outbox는 보낸 이벤트를 바로 지우므로, 발행한 이벤트를 테이블에서 확인하려고 테스트에서는 전송을 멈춘다.
// ai-service로 밀어 주기는 가짜 ai-service(FakeAiService)로 보내고, 기다리는 시간을 줄인다.
// 회의방 WebSocket을 실제로 붙여 보려고 실제 포트로 띄운다 (MockMvc도 함께 쓴다)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "nineouting.outbox.relay-interval=1h",
        "nineouting.meeting.ai.batch-interval=1s",
        "nineouting.meeting.ai.poll-interval=200ms",
        "nineouting.meeting.ai.health-interval=200ms"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    /** 테스트마다 겹치지 않는 ID */
    private static final AtomicLong NEXT_ID = new AtomicLong(1000);
    private static final Map<Long, FakeFolder> FOLDERS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected OutboxEventRepository outboxEventRepository;

    @MockitoBean
    protected UserClient userClient;

    @MockitoBean
    protected WorkspaceClient workspaceClient;

    @DynamicPropertySource
    static void fakeAiService(DynamicPropertyRegistry registry) {
        registry.add("nineouting.meeting.ai.base-url", FakeAiService::baseUrl);
    }

    @BeforeEach
    void fakeOtherServices() {
        given(userClient.findUsers(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(),
                    id -> new UserSummary(id, "user" + id, "user" + id + "@example.com", null), (a, b) -> a));
        });
        given(workspaceClient.access(anyLong(), anyLong())).willAnswer(invocation -> {
            FakeFolder folder = requireFolder(invocation.getArgument(0));
            return new FolderAccess(folder.id(), folder.workspaceId(), folder.accessors().contains(invocation.<Long>getArgument(1)));
        });
        given(workspaceClient.folderMembers(anyLong()))
                .willAnswer(invocation -> Set.copyOf(requireFolder(invocation.getArgument(0)).accessors()));
        given(workspaceClient.accessibleFolders(anyLong())).willAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return FOLDERS.values().stream()
                    .filter(folder -> folder.accessors().contains(userId))
                    .map(folder -> new AccessibleFolder(folder.id(), folder.workspaceId(), "워크스페이스" + folder.workspaceId(),
                            null, folder.name()))
                    .toList();
        });
    }

    protected static Long newId() {
        return NEXT_ID.incrementAndGet();
    }

    /** 가짜 workspace-service에 폴더를 만든다. accessors = 폴더에 들어갈 수 있는 사람 */
    protected static Long folder(Long workspaceId, String name, Long... accessors) {
        Long id = newId();
        FOLDERS.put(id, new FakeFolder(id, workspaceId, name, new LinkedHashSet<>(List.of(accessors))));
        return id;
    }

    private static FakeFolder requireFolder(Long folderId) {
        FakeFolder folder = FOLDERS.get(folderId);
        if (folder == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return folder;
    }

    /** 안건 1개짜리 회의를 만든다 */
    protected Long createMeeting(Long folderId, Long ownerId, Long writerId, Long... attendees) throws Exception {
        return id(call(ownerId, post("/api/folders/" + folderId + "/meetings"),
                meetingJson("회의", "2026-10-20T14:00:00", "2026-10-20T15:00:00", writerId, List.of(attendees),
                        "{\"title\":\"안건1\",\"decisionPoint\":\"결정1\"}"))
                .andExpect(status().isCreated()), "$.data.meetingId");
    }

    protected static String meetingJson(String title, String startAt, String endAt, Long writerId, List<Long> attendees,
                                        String... agendas) {
        return """
                {"title":"%s","startAt":"%s","endAt":"%s","location":"3층 회의실","attendeeUserIds":%s,
                 "writerUserId":%d,"agendas":[%s]}"""
                .formatted(title, startAt, endAt, attendees, writerId, String.join(",", agendas));
    }

    protected ResultActions call(Long userId, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestcontainersConfiguration.token(userId));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    protected boolean outboxContains(String eventType, String text) {
        return outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getEventType().equals(eventType) && event.getPayload().contains(text));
    }

    protected static Long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    protected static <T> T data(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private record FakeFolder(Long id, Long workspaceId, String name, Set<Long> accessors) {
    }
}
