package com.nineouting.backend.meeting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.meeting.config.MeetingProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** workspace-service 대신 JDK HTTP 서버를 띄워 실제 HTTP 호출과 응답 해석을 확인한다. */
class WorkspaceClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 폴더_권한_멤버_목록을_해석한다() throws IOException {
        WorkspaceClient client = clientFor(startWorkspaceService());

        assertThat(client.access(7L, 3L)).isEqualTo(new FolderAccess(7L, 1L, true));
        assertThat(client.folderMembers(7L)).containsExactlyInAnyOrder(3L, 4L);
        assertThat(client.accessibleFolders(3L)).containsExactly(new AccessibleFolder(7L, 1L, "팀", null, "기획"));
    }

    @Test
    void 없는_폴더는_404다() throws IOException {
        WorkspaceClient client = clientFor(startWorkspaceService());

        assertThatThrownBy(() -> client.access(404L, 3L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void workspace_service에_연결할_수_없으면_503이다() {
        WorkspaceClient client = clientFor("http://127.0.0.1:1");

        assertThatThrownBy(() -> client.folderMembers(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo("WORKSPACE_SERVICE_UNAVAILABLE");
    }

    private String startWorkspaceService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/folders/7/access", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"folderId\":7,\"workspaceId\":1,\"allowed\":true},\"message\":\"\"}"));
        server.createContext("/internal/folders/7/members", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":[3,4],\"message\":\"\"}"));
        server.createContext("/internal/users/3/folders", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":[{\"folderId\":7,\"workspaceId\":1,\"workspaceName\":\"팀\",\"parentId\":null,"
                        + "\"name\":\"기획\"}],\"message\":\"\"}"));
        server.createContext("/internal/folders/404/access", exchange -> respond(exchange, 404,
                "{\"success\":false,\"data\":null,\"message\":\"요청한 대상을 찾을 수 없습니다\",\"code\":\"NOT_FOUND\"}"));
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static WorkspaceClient clientFor(String baseUrl) {
        return new WorkspaceClient(RestClient.builder(), new MeetingProperties("http://localhost:8081", baseUrl));
    }
}
