package com.nineouting.backend.meeting;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 이 회의로 받은 요청 본문. kind: events / lines */
    public static List<String> received(Long meetingId, String kind) {
        String path = "/meetings/" + meetingId + "/" + kind;
        return RECEIVED.stream().filter(received -> received.path().equals(path)).map(Received::body).toList();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int conflict = path.endsWith("/lines") ? CONFLICT_ONCE.getAndSet(0) : 0;
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
