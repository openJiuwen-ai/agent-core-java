
package com.openjiuwen.core.common.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class HttpClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        HttpSessionManager.getInstance().closeAll();
        ConnectorPoolManager.getInstance().resetForTests();
    }

    @Test
    void sessionConfigKeyShouldBeStable() {
        SessionConfig first = new SessionConfig(new ConnectorPoolConfig(), Map.of("User-Agent", "Test"), null, 30.0,
                10.0, Map.of("sock_read_timeout", 5), null, false, true, Map.of());
        SessionConfig second = new SessionConfig(new ConnectorPoolConfig(), Map.of("User-Agent", "Test"), null, 30.0,
                10.0, Map.of("sock_read_timeout", 5), null, false, true, Map.of());

        assertThat(first.generateKey()).isEqualTo(second.generateKey());
    }

    @Test
    void shouldReuseManagedSessionWhenConfigured() throws Exception {
        HttpSessionManager manager = HttpSessionManager.getInstance();
        SessionConfig config = new SessionConfig();

        BaseRefResourceMgr.Acquisition<HttpSession> first = manager.acquire(config);
        BaseRefResourceMgr.Acquisition<HttpSession> second = manager.acquire(config);

        assertThat(second.resource()).isSameAs(first.resource());
        assertThat(first.resource().getRefCount()).isEqualTo(2);

        manager.release(config);
        manager.release(config);
        assertThat(first.resource().isClosed()).isTrue();
    }

    @Test
    void shouldIssueGetRequestAndParseJsonBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/json", new JsonHandler());
        server.start();

        int port = server.getAddress().getPort();
        HttpClient client = new HttpClient(new SessionConfig(new ConnectorPoolConfig(),
                Map.of("User-Agent", "Codex-Test"), null, 10.0, null, Map.of(), null, false, true, Map.of()));

        Map<String, Object> response = client.get("http://127.0.0.1:" + port + "/json", Map.of("q", "1"));

        assertThat(response.get("code")).isEqualTo(200);
        assertThat(response.get("data")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        Map<String, java.util.List<String>> headers = (Map<String, java.util.List<String>>) response.get("headers");
        assertThat(body).containsEntry("message", "ok");
        assertThat(headers).containsKey("content-type");
        client.close();
    }

    @Test
    void shouldReuseClientSessionAcrossCalls() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/text", exchange -> writeResponse(exchange, "text/plain", "hello"));
        server.start();

        int port = server.getAddress().getPort();
        HttpClient client = new HttpClient(new SessionConfig(), true);

        Map<String, Object> first = client.get("http://127.0.0.1:" + port + "/text");
        Map<String, Object> second = client.get("http://127.0.0.1:" + port + "/text");

        assertThat(first.get("data")).isEqualTo("hello");
        assertThat(second.get("data")).isEqualTo("hello");
        client.close();
        assertThat(client.isClosed()).isTrue();
    }

    @Test
    void shouldSupportAsyncRequest() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/async",
                exchange -> writeResponse(exchange, "application/json", "{\"message\":\"async\"}"));
        server.start();

        int port = server.getAddress().getPort();
        HttpClient client = new HttpClient(new SessionConfig(), true);

        CompletableFuture<Map<String, Object>> future =
            client.requestAsync("GET", "http://127.0.0.1:" + port + "/async", null, null, null, Map.of());

        Map<String, Object> response = future.join();
        assertThat(response.get("code")).isEqualTo(200);
        assertThat(String.valueOf(((Map<?, ?>) response.get("data")).get("message"))).isEqualTo("async");
    }

    @Test
    void shouldSupportStreamGet() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> writeResponse(exchange, "text/plain", "line1\nline2\n"));
        server.start();

        int port = server.getAddress().getPort();
        HttpClient client = new HttpClient(new SessionConfig(), true);

        Iterator<Object> iterator = client.streamGet("http://127.0.0.1:" + port + "/stream", Map.of(), false, 1024);
        List<String> lines = new ArrayList<>();
        while (iterator.hasNext()) {
            lines.add(new String((byte[]) iterator.next(), StandardCharsets.UTF_8));
        }

        assertThat(lines).containsExactly("line1", "line2");
    }

    private static final class JsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            writeResponse(exchange, "application/json", "{\"message\":\"ok\"}");
        }
    }

    private static void writeResponse(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of(contentType));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
