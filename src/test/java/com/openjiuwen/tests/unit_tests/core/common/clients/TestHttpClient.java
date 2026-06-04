// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.tests.unit_tests.core.common.clients;

import com.openjiuwen.core.common.clients.http.HttpClient;
import com.openjiuwen.core.common.clients.http.HttpSession;
import com.openjiuwen.core.common.clients.http.HttpSessionManager;
import com.openjiuwen.core.common.clients.http.SessionConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_http_client} in
 * {@code tests.unit_tests.core.common.clients.test_http_client}.
 */
@Tag("unit-test")
class TestHttpClient {

    @AfterEach
    void tearDown() {
        HttpSessionManager.getInstance().clear();
    }

    @Test
    @Tag("level0")
    @DisplayName("Test custom SessionConfig values")
    void testCustomValues() {
        SessionConfig config = new SessionConfig(
                30.0,
                10.0,
                true,
                Map.of("User-Agent", "Test"),
                "http://proxy:8080"
        );

        assertEquals(30.0, config.getTimeout());
        assertEquals(10.0, config.getConnectTimeout());
        assertTrue(config.isRaiseForStatus());
        assertEquals(Map.of("User-Agent", "Test"), config.getHeaders());
        assertEquals("http://proxy:8080", config.getProxy());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test default SessionConfig values")
    void testDefaultValues() {
        SessionConfig config = new SessionConfig();

        assertNotNull(config.getConnectorPoolConfig());
        assertTrue(config.getHeaders().isEmpty());
        assertTrue(config.getTimeoutArgs().isEmpty());
        assertTrue(config.getExtendArgs().isEmpty());
        assertFalse(config.isRaiseForStatus());
        assertTrue(config.isTrustEnv());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test SessionConfig generateKey equality")
    void testGenerateKey() {
        SessionConfig config1 = new SessionConfig(30.0, Map.of("User-Agent", "Test"));
        SessionConfig config2 = new SessionConfig(30.0, Map.of("User-Agent", "Test"));
        SessionConfig config3 = new SessionConfig(60.0, Map.of("User-Agent", "Test"));

        assertEquals(config1.generateKey(), config2.generateKey());
        assertNotEquals(config1.generateKey(), config3.generateKey());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test SessionConfig generateKey with complex types")
    void testGenerateKeyWithComplexTypes() {
        Map<String, String> headers = new HashMap<>();
        headers.put("b", "2");
        headers.put("a", "1");
        SessionConfig config = new SessionConfig();
        config.setHeaders(headers);
        config.setTimeoutArgs(Map.of("sock_read_timeout", "30", "sock_connect_timeout", "10"));

        String key = config.generateKey();

        assertNotNull(key);
        assertFalse(key.isBlank());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test SessionConfig proxy participates in key")
    void testGenerateKeyIncludesProxy() {
        SessionConfig noProxy = new SessionConfig(30.0, Map.of("User-Agent", "Test"));
        SessionConfig withProxy = new SessionConfig(30.0, 10.0, false,
                Map.of("User-Agent", "Test"), "http://proxy:8080");

        assertNotEquals(noProxy.generateKey(), withProxy.generateKey());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSession initialization")
    void testInit() {
        java.net.http.HttpClient rawClient = java.net.http.HttpClient.newHttpClient();
        SessionConfig config = new SessionConfig(30.0, (Map<String, String>) null);
        HttpSession session = new HttpSession(rawClient, config);

        assertSame(rawClient, session.session());
        assertSame(config, session.getConfig());
        assertFalse(session.isClosed());
        assertEquals(1, session.getRefCount());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSession session method")
    void testSessionMethod() {
        java.net.http.HttpClient rawClient = java.net.http.HttpClient.newHttpClient();
        HttpSession session = new HttpSession(rawClient, new SessionConfig());

        assertSame(rawClient, session.session());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSession session method after close")
    void testSessionMethodClosed() {
        HttpSession session = new HttpSession(java.net.http.HttpClient.newHttpClient(), new SessionConfig());
        session.close();

        RuntimeException thrown = assertThrows(RuntimeException.class, session::session);
        assertTrue(thrown.getMessage().contains("Session is closed"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSession close")
    void testDoClose() {
        HttpSession session = new HttpSession(java.net.http.HttpClient.newHttpClient(), new SessionConfig());

        session.close();

        assertTrue(session.isClosed());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSessionManager acquire new session")
    void testAcquireNewSession() {
        HttpSessionManager manager = HttpSessionManager.getInstance();
        SessionConfig config = new SessionConfig(30.0, (Map<String, String>) null);

        HttpSession session = manager.acquire(config);

        assertNotNull(session);
        assertSame(config, session.getConfig());
        assertEquals(1, session.getRefCount());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSessionManager acquire existing session")
    void testAcquireExistingSession() {
        HttpSessionManager manager = HttpSessionManager.getInstance();
        SessionConfig config = new SessionConfig(30.0, (Map<String, String>) null);

        HttpSession session1 = manager.acquire(config);
        HttpSession session2 = manager.acquire(config);

        assertSame(session1, session2);
        assertEquals(2, session1.getRefCount());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSessionManager release session")
    void testReleaseSession() {
        HttpSessionManager manager = HttpSessionManager.getInstance();
        SessionConfig config = new SessionConfig(30.0, (Map<String, String>) null);
        HttpSession session = manager.acquire(config);

        manager.releaseSession(config);

        assertTrue(session.isClosed());
        HttpSession replacement = manager.acquire(config);
        assertNotEquals(session, replacement);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSessionManager default acquire")
    void testDefaultAcquire() {
        HttpSession session = HttpSessionManager.getInstance().acquire();

        assertNotNull(session);
        assertNotNull(session.session());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpSessionManager clear")
    void testManagerClear() {
        HttpSessionManager manager = HttpSessionManager.getInstance();
        SessionConfig config = new SessionConfig(30.0, (Map<String, String>) null);
        HttpSession session = manager.acquire(config);

        manager.clear();

        assertTrue(session.isClosed());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpClient stores config")
    void testHttpClientConfig() {
        SessionConfig config = new SessionConfig(30.0, Map.of("User-Agent", "Test"));
        HttpClient client = new HttpClient(config);

        assertSame(config, client.getConfig());
        assertTrue(client.isHealthy());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpClient close")
    void testClose() {
        HttpClient client = new HttpClient();

        client.close();

        assertFalse(client.isHealthy());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpClient initialize from map")
    void testInitializeFromMap() {
        HttpClient client = new HttpClient();
        Map<String, Object> rawConfig = Map.of(
                "timeout", 30.0,
                "connect_timeout", 10.0,
                "raise_for_status", true,
                "headers", Map.of("User-Agent", "Test")
        );

        client.initialize(rawConfig);

        assertEquals(30.0, client.getConfig().getTimeout());
        assertEquals(10.0, client.getConfig().getConnectTimeout());
        assertTrue(client.getConfig().isRaiseForStatus());
        assertEquals("Test", client.getConfig().getHeaders().get("User-Agent"));
        assertTrue(client.isHealthy());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpClient name and type")
    void testClientNameAndType() {
        assertEquals("http", HttpClient.getClientName());
        assertEquals("common", HttpClient.getClientType());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test synchronous GET request")
    void testRequestSuccess() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "application/json", "{\"data\":\"test\"}"));
        try {
            HttpResponse<String> response = new HttpClient().get(url(server, "/"));

            assertEquals(200, response.statusCode());
            assertEquals("{\"data\":\"test\"}", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test synchronous POST request")
    void testPostRequest() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 201, "text/plain", "created");
        });
        try {
            HttpResponse<String> response = new HttpClient().post(url(server, "/"), "{\"key\":\"value\"}");

            assertEquals(201, response.statusCode());
            assertEquals("created", response.body());
            assertEquals("{\"key\":\"value\"}", receivedBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test async GET request")
    void testAsyncGet() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "text/plain", "ok"));
        try {
            CompletableFuture<HttpResponse<String>> future = new HttpClient().getAsync(url(server, "/"));

            assertEquals("ok", future.join().body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test async POST request")
    void testAsyncPost() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 202, "text/plain", "accepted"));
        try {
            HttpResponse<String> response = new HttpClient().postAsync(url(server, "/"), "{}").join();

            assertEquals(202, response.statusCode());
            assertEquals("accepted", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test configured headers are forwarded")
    void testCustomHeadersAndParams() throws Exception {
        AtomicReference<String> headerValue = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            headerValue.set(exchange.getRequestHeaders().getFirst("X-Custom"));
            write(exchange, 200, "text/plain", "ok");
        });
        SessionConfig config = new SessionConfig(30.0, Map.of("X-Custom", "value"));
        try {
            new HttpClient(config).get(url(server, "/"));

            assertEquals("value", headerValue.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HTTP error response is returned")
    void testRequestWithHttpError() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 404, "text/plain", "Not Found"));
        try {
            HttpResponse<String> response = new HttpClient().get(url(server, "/missing"));

            assertEquals(404, response.statusCode());
            assertEquals("Not Found", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test different content types")
    void testDifferentContentTypes() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200,
                "application/octet-stream", "binary data"));
        try {
            HttpResponse<String> response = new HttpClient().get(url(server, "/binary"));

            assertEquals(200, response.statusCode());
            assertEquals("binary data", response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/octet-stream"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test invalid URL surfaces as runtime error")
    void testErrorHandling() {
        HttpClient client = new HttpClient();

        assertThrows(IllegalArgumentException.class, () -> client.get("not a uri"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test timeout value is adapted to Duration")
    void testRequestWithTimeout() {
        SessionConfig config = new SessionConfig();
        config.setTimeout(0.5);
        HttpClient client = new HttpClient(config);

        assertDoesNotThrow(() -> client.initialize(Map.of("timeout", 0.5)));
        assertEquals(Duration.ofMillis(500), Duration.ofMillis(Math.round(client.getConfig().getTimeout() * 1000)));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test request headers merge config and call headers")
    void testBuildRequestHeadersMergesDefaultsAndOverrides() {
        SessionConfig config = new SessionConfig(30.0, Map.of("X-Default", "base", "X-Override", "base"));
        HttpClient client = new HttpClient(config);

        Map<String, String> headers = client.buildRequestHeaders(Map.of("X-Override", "call", "X-Trace", 123));

        assertEquals("base", headers.get("X-Default"));
        assertEquals("call", headers.get("X-Override"));
        assertEquals("123", headers.get("X-Trace"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test GET request appends query params")
    void testGetWithParamsAppendsQuery() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            write(exchange, 200, "text/plain", "ok");
        });
        try {
            HttpResponse<String> response = new HttpClient().get(url(server, "/search"),
                    Map.of("q", "hello world", "page", 2));

            assertEquals(200, response.statusCode());
            assertTrue(query.get().contains("q=hello+world"));
            assertTrue(query.get().contains("page=2"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test PUT request")
    void testPutRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, "text/plain", "updated");
        });
        try {
            HttpResponse<String> response = new HttpClient().put(url(server, "/items/1"), "{\"name\":\"new\"}");

            assertEquals("PUT", method.get());
            assertEquals("{\"name\":\"new\"}", body.get());
            assertEquals("updated", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test DELETE request")
    void testDeleteRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            method.set(exchange.getRequestMethod());
            write(exchange, 204, "text/plain", "");
        });
        try {
            HttpResponse<String> response = new HttpClient().delete(url(server, "/items/1"));

            assertEquals("DELETE", method.get());
            assertEquals(204, response.statusCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test PATCH request")
    void testPatchRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, "text/plain", "patched");
        });
        try {
            HttpResponse<String> response = new HttpClient().patch(url(server, "/items/1"), "{\"op\":\"replace\"}");

            assertEquals("PATCH", method.get());
            assertEquals("{\"op\":\"replace\"}", body.get());
            assertEquals("patched", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HEAD request")
    void testHeadRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            method.set(exchange.getRequestMethod());
            exchange.getResponseHeaders().set("X-Head", "ok");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        try {
            HttpResponse<String> response = new HttpClient().head(url(server, "/head"));

            assertEquals("HEAD", method.get());
            assertEquals(204, response.statusCode());
            assertEquals("ok", response.headers().firstValue("X-Head").orElse(""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test OPTIONS request")
    void testOptionsRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            method.set(exchange.getRequestMethod());
            exchange.getResponseHeaders().set("Allow", "GET,POST,OPTIONS");
            write(exchange, 200, "text/plain", "options");
        });
        try {
            HttpResponse<String> response = new HttpClient().options(url(server, "/"));

            assertEquals("OPTIONS", method.get());
            assertEquals("GET,POST,OPTIONS", response.headers().firstValue("Allow").orElse(""));
            assertEquals("options", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test request with size limit")
    void testRequestWithSizeLimit() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "text/plain", "small"));
        try {
            HttpResponse<String> response = new HttpClient().requestWithSizeLimit("GET", url(server, "/"), 10);

            assertEquals(200, response.statusCode());
            assertEquals("small", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test request with size limit rejects large response")
    void testRequestWithSizeLimitRejectsLargeResponse() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "text/plain", "too-large"));
        try {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new HttpClient().requestWithSizeLimit("GET", url(server, "/"), 3));

            assertTrue(thrown.getMessage().contains("Response too large"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test stream request")
    void testStreamRequest() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "text/plain", "abcdef"));
        try {
            List<byte[]> chunks = new HttpClient().streamGet(url(server, "/"), 2);

            assertEquals(List.of("ab", "cd", "ef"), chunksAsStrings(chunks));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test stream request with callback")
    void testStreamRequestWithCallback() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "text/plain", "abcd"));
        try {
            List<byte[]> chunks = new HttpClient().streamGet(url(server, "/"), 2,
                    chunk -> new String(chunk, StandardCharsets.UTF_8).toUpperCase().getBytes(StandardCharsets.UTF_8));

            assertEquals(List.of("AB", "CD"), chunksAsStrings(chunks));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test closed HttpClient rejects requests")
    void testAcquireSessionClosedClient() {
        HttpClient client = new HttpClient();
        client.close();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> client.request("GET", "http://127.0.0.1/"));

        assertTrue(thrown.getMessage().contains("HttpClient is closed"));
        assertTrue(client.isClosed());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test concurrent GET requests")
    void testConcurrentRequests() throws Exception {
        HttpServer server = createServer(exchange -> write(exchange, 200, "text/plain",
                exchange.getRequestURI().getPath()));
        try {
            HttpClient client = new HttpClient();
            List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(client.getAsync(url(server, "/" + i)));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();

            for (int i = 0; i < futures.size(); i++) {
                assertEquals("/" + i, futures.get(i).join().body());
            }
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer createServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void write(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static List<String> chunksAsStrings(List<byte[]> chunks) {
        List<String> values = new ArrayList<>();
        for (byte[] chunk : chunks) {
            values.add(new String(chunk, StandardCharsets.UTF_8));
        }
        return values;
    }
}
