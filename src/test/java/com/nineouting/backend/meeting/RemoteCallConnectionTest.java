package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.FolderAccess;
import com.nineouting.backend.common.client.UserSummary;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 다른 서비스(workspace, user)를 기다리는 동안 DB 연결을 쥐고 있지 않는지 (2026-11-26 부하 시험).
 * 가짜 클라이언트가 불릴 때 사용 중인 연결 수를 잰다. 시험은 한 번에 하나씩 돌아 다른 요청이 연결을 쓰지 않는다
 */
class RemoteCallConnectionTest extends ApiTestSupport {

    @Autowired
    DataSource dataSource;

    private final List<Integer> activeDuringRemoteCall = new ArrayList<>();
    private Long owner;
    private Long folderId;

    @BeforeEach
    void recordConnectionsDuringRemoteCalls() throws Exception {
        owner = newId();
        Long workspaceId = newId();
        folderId = folder(workspaceId, "기획", owner);
        Long meetingId = createMeeting(folderId, owner, owner, owner);
        call(owner, post("/api/meetings/" + meetingId + "/start"), null).andExpect(status().isOk());
        call(owner, post("/api/meetings/" + meetingId + "/finish"), null).andExpect(status().isOk());

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        // 회의를 끝내면 뒤에서 도는 작업도 같은 가짜 클라이언트를 부른다. 요청을 처리한 이 스레드의 호출만 센다
        Thread testThread = Thread.currentThread();
        willAnswer(invocation -> {
            if (Thread.currentThread() == testThread) {
                activeDuringRemoteCall.add(hikari.getHikariPoolMXBean().getActiveConnections());
            }
            return new FolderAccess(folderId, workspaceId, true, true);
        }).given(workspaceClient).access(anyLong(), anyLong());
        willAnswer(invocation -> {
            if (Thread.currentThread() == testThread) {
                activeDuringRemoteCall.add(hikari.getHikariPoolMXBean().getActiveConnections());
            }
            return List.of(new AccessibleFolder(folderId, workspaceId, "워크스페이스", null, "기획"));
        }).given(workspaceClient).accessibleFolders(anyLong());
        willAnswer(invocation -> {
            if (Thread.currentThread() == testThread) {
                activeDuringRemoteCall.add(hikari.getHikariPoolMXBean().getActiveConnections());
            }
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(),
                    id -> new UserSummary(id, "user" + id, "user" + id + "@example.com", null), (a, b) -> a));
        }).given(userClient).findUsers(anyCollection(), any());
        awaitIdle(hikari);
        activeDuringRemoteCall.clear();
    }

    /** 회의를 끝낸 뒤 뒤에서 도는 작업이 연결을 다 돌려줄 때까지 (최대 10초) */
    private static void awaitIdle(HikariDataSource hikari) throws InterruptedException {
        for (int i = 0; i < 100 && hikari.getHikariPoolMXBean().getActiveConnections() > 0; i++) {
            Thread.sleep(100);
        }
    }

    @Test
    void 폴더_회의_목록() throws Exception {
        call(owner, get("/api/folders/" + folderId + "/meetings"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    @Test
    void 캘린더() throws Exception {
        call(owner, get("/api/meetings/calendar?from=2026-10-01&to=2026-10-31"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    @Test
    void 내_할_일() throws Exception {
        call(owner, get("/api/meetings/my-todos"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    private void assertNoConnectionHeld() {
        assertThat(activeDuringRemoteCall).isNotEmpty().allMatch(active -> active == 0);
    }
}
