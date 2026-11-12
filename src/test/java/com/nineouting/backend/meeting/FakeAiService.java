package com.nineouting.backend.meeting;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 가짜 ai-service. 받은 요청(성공한 것만)을 쌓아 두고, 정한 대로 응답한다.
 * 모든 API 테스트가 같은 서버를 쓰므로 요청은 회의 ID로 골라 본다.
 */
public final class FakeAiService {

    private static final HttpServer SERVER;
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger STATUS = new AtomicInteger(200);
    /** 0보다 크면 다음 줄 묶음 한 번을 409로 거절하고 이 번호를 기다린다고 답한다 */
    private static final AtomicInteger CONFLICT_ONCE = new AtomicInteger();
    /** 0보다 크면 다음 요청 한 번을 이 상태 코드로 거절한다 (kind별: events / batches) */
    private static final Map<String, AtomicInteger> REJECT_ONCE = Map.of(
            "events", new AtomicInteger(), "batches", new AtomicInteger());
    /** REQ-185 챗봇 답 (POST /internal/rag/chat). 상태 코드가 200이 아니면 그 코드로 거절한다 */
    private static final AtomicReference<String> CHAT_ANSWER = new AtomicReference<>(
            "{\"status\":\"no_result\",\"basis\":null,\"answer\":\"회의 자료에서 찾을 수 없어요\",\"citations\":[],\"errorCode\":null}");
    private static final AtomicInteger CHAT_STATUS = new AtomicInteger(200);
    /** minutes-service 대신: 폴더 번호 → 작성 완료 회의록 번호 (GET /internal/minutes/rag-docs) */
    private static final Map<Long, String> MINUTES_DOCS = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SERVER.createContext("/", FakeAiService::handle);
        SERVER.start();
    }

    private FakeAiService() {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** ai-service가 장애일 때처럼 응답하게 한다 (200으로 되돌릴 때도 쓴다) */
    public static void respondWith(int status) {
        STATUS.set(status);
    }

    public static void conflictOnce(int expectedBatch) {
        CONFLICT_ONCE.set(expectedBatch);
    }

    /** kind(events / batches)의 다음 요청 한 번을 status로 거절한다 */
    public static void rejectOnce(String kind, int status) {
        REJECT_ONCE.get(kind).set(status);
    }

    public static void chatAnswer(int status, String body) {
        CHAT_STATUS.set(status);
        CHAT_ANSWER.set(body);
    }

    public static void minutesDocs(Long folderId, String idsJson) {
        MINUTES_DOCS.put(folderId, idsJson);
    }

    /** RAG 요청 중 경로가 path로 시작하는 것 (메서드와 함께: "POST /internal/rag/documents") */
    public static List<String> receivedRag(String methodAndPath) {
        return RECEIVED.stream().filter(received -> received.path().startsWith(methodAndPath))
                .map(Received::body).toList();
    }

    /** 이 회의로 받은 요청 본문. kind: events / batches */
    public static List<String> received(Long meetingId, String kind) {
        String path = "/internal/meetings/" + meetingId + "/" + kind;
        return RECEIVED.stream().filter(received -> received.path().equals(path)).map(Received::body).toList();
    }

    /** 이 회의로 받은 요청 종류(events / batches)를 받은 순서대로 */
    public static List<String> receivedKinds(Long meetingId) {
        String prefix = "/internal/meetings/" + meetingId + "/";
        return RECEIVED.stream().filter(received -> received.path().startsWith(prefix))
                .map(received -> received.path().substring(prefix.length())).toList();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.startsWith("/internal/rag/") || path.startsWith("/internal/minutes/")) {
            handleRag(exchange, path, body);
            return;
        }
        String kind = path.substring(path.lastIndexOf('/') + 1);
        int reject = REJECT_ONCE.containsKey(kind) ? REJECT_ONCE.get(kind).getAndSet(0) : 0;
        if (reject > 0) {
            respond(exchange, reject, "{\"error\":\"INVALID_INPUT\",\"detail\":\"시험용 거절\"}");
            return;
        }
        int conflict = path.endsWith("/batches") ? CONFLICT_ONCE.getAndSet(0) : 0;
        if (conflict > 0) {
            respond(exchange, 409, "{\"expectedBatch\":" + conflict + "}");
            return;
        }
        int status = STATUS.get();
        if (status < 300) {
            RECEIVED.add(new Received(path, body));
        }
        respond(exchange, status, "");
    }

    private static void handleRag(HttpExchange exchange, String path, String body) throws IOException {
        String method = exchange.getRequestMethod();
        if (path.equals("/internal/minutes/rag-docs")) {
            String query = exchange.getRequestURI().getQuery();
            Long folderId = Long.valueOf(query.substring(query.indexOf('=') + 1));
            respond(exchange, 200, MINUTES_DOCS.getOrDefault(folderId, "[]"));
            return;
        }
        int status = path.equals("/internal/rag/chat") ? CHAT_STATUS.get() : STATUS.get();
        if (status >= 300) {
            respond(exchange, status, "{\"error\":\"NOT_READY\"}");
            return;
        }
        RECEIVED.add(new Received(method + " " + path, body));
        switch (method + " " + path.replaceAll("/\\d+$", "/{id}")) {
            case "POST /internal/rag/documents" -> respond(exchange, 202, "{\"status\":\"accepted\"}");
            case "POST /internal/rag/chat" -> respond(exchange, 200, CHAT_ANSWER.get());
            case "GET /internal/rag/documents/upload/{id}" -> respond(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
            default -> respond(exchange, 204, "");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private record Received(String path, String body) {
    }
}
