/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseRefResourceMgr;
import com.openjiuwen.core.common.clients.SessionConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests/unit_tests/core/common/clients/test_http_client.py}.
 */
class HttpClientPythonParityTest {

    @AfterEach
    void tearDown() {
        HttpSessionManager.getInstance().closeAll().join();
    }

    @Test
    void sessionConfigCustomValues() {
        SessionConfig config = new SessionConfig(mapOf(
                "timeout", 30.0d,
                "connect_timeout", 10.0d,
                "raise_for_status", true,
                "headers", Map.of("User-Agent", "Test"),
                "proxy", "http://proxy:8080"
        ));

        assertThat(config.getTimeout()).isEqualTo(30.0d);
        assertThat(config.getConnectTimeout()).isEqualTo(10.0d);
        assertThat(config.isRaiseForStatus()).isTrue();
        assertThat(config.getHeaders()).isEqualTo(Map.of("User-Agent", "Test"));
        assertThat(config.getProxy()).isEqualTo("http://proxy:8080");
    }

    @Test
    void sessionConfigGenerateKeyIsStableForEqualValues() {
        SessionConfig config1 = new SessionConfig(mapOf("timeout", 30.0d, "headers", Map.of("User-Agent", "Test")));
        SessionConfig config2 = new SessionConfig(mapOf("timeout", 30.0d, "headers", Map.of("User-Agent", "Test")));
        SessionConfig config3 = new SessionConfig(mapOf("timeout", 60.0d, "headers", Map.of("User-Agent", "Test")));

        assertThat(config1.generateKey()).isEqualTo(config2.generateKey());
        assertThat(config1.generateKey()).isNotEqualTo(config3.generateKey());
    }

    @Test
    void sessionConfigGenerateKeyWithComplexTypes() {
        SessionConfig config = new SessionConfig(mapOf(
                "headers", Map.of("b", "2", "a", "1"),
                "timeout_args", Map.of("sock_read", "30", "sock_connect", "10")
        ));

        assertThat(config.generateKey()).isNotBlank();
    }

    @Test
    void httpSessionInit() {
        SessionConfig config = new SessionConfig(mapOf("timeout", 30.0d));
        java.net.http.HttpClient rawClient = java.net.http.HttpClient.newHttpClient();

        HttpSession session = new HttpSession(rawClient, config);

        assertThat(session.session()).isSameAs(rawClient);
        assertThat(session.isClosed()).isFalse();
        assertThat(session.getRefCount()).isEqualTo(1);
    }

    @Test
    void httpSessionMethodReturnsWrappedClient() {
        java.net.http.HttpClient rawClient = java.net.http.HttpClient.newHttpClient();
        HttpSession session = new HttpSession(rawClient, new SessionConfig());

        assertThat(session.session()).isSameAs(rawClient);
    }

    @Test
    void httpSessionMethodClosedRaises() {
        HttpSession session = new HttpSession(java.net.http.HttpClient.newHttpClient(), new SessionConfig());

        session.close().join();

        assertThatThrownBy(session::session)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Session is closed");
    }

    @Test
    void httpSessionDoCloseMarksClosed() {
        HttpSession session = new HttpSession(java.net.http.HttpClient.newHttpClient(), new SessionConfig());

        session.close().join();

        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void sessionManagerCreateResource() {
        HttpSessionManager manager = new HttpSessionManager();
        SessionConfig config = new SessionConfig(mapOf("timeout", 30.0d));

        BaseRefResourceMgr.ResourceLease<HttpSession> lease = manager.acquire(config).join();

        assertThat(lease.resource()).isInstanceOf(HttpSession.class);
        assertThat(lease.isNew()).isTrue();
        manager.releaseSession(config).join();
    }

    @Test
    void sessionManagerAcquireNewSession() {
        HttpSessionManager manager = new HttpSessionManager();
        SessionConfig config = new SessionConfig(mapOf("timeout", 30.0d));

        BaseRefResourceMgr.ResourceLease<HttpSession> lease = manager.acquire(config).join();

        assertThat(lease.isNew()).isTrue();
        assertThat(lease.resource()).isInstanceOf(HttpSession.class);
        manager.releaseSession(config).join();
    }

    @Test
    void sessionManagerAcquireExistingSession() {
        HttpSessionManager manager = new HttpSessionManager();
        SessionConfig config = new SessionConfig(mapOf("timeout", 30.0d));

        BaseRefResourceMgr.ResourceLease<HttpSession> first = manager.acquire(config).join();
        BaseRefResourceMgr.ResourceLease<HttpSession> second = manager.acquire(config).join();

        assertThat(first.isNew()).isTrue();
        assertThat(second.isNew()).isFalse();
        assertThat(second.resource()).isSameAs(first.resource());
        manager.releaseSession(config).join();
        manager.releaseSession(config).join();
    }

    @Test
    void sessionManagerReleaseSessionCreatesNewAfterRelease() {
        HttpSessionManager manager = new HttpSessionManager();
        SessionConfig config = new SessionConfig(mapOf("timeout", 30.0d));

        HttpSession first = manager.acquire(config).join().resource();
        manager.releaseSession(config).join();
        HttpSession second = manager.acquire(config).join().resource();

        assertThat(second).isNotSameAs(first);
        manager.releaseSession(config).join();
    }

    @Test
    void sessionManagerWithSessionContextReleasesSession() {
        HttpSessionManager manager = new HttpSessionManager();
        SessionConfig config = new SessionConfig(mapOf("timeout", 30.0d));

        HttpSession session = manager.withSession(config, current -> CompletableFuture.completedFuture(current)).join();

        assertThat(session).isInstanceOf(HttpSession.class);
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void httpClientContextManagerClose() {
        HttpClient client = new HttpClient(new SessionConfig(mapOf("timeout", 30.0d)));

        assertThat(client.enter()).isSameAs(client);
        assertThat(client.isClosed()).isFalse();
        client.close().join();

        assertThat(client.isClosed()).isTrue();
    }

    @Test
    void acquireSessionReusableKeepsSameSessionAcrossRequests() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), true);

            client.get(server.url("/json")).join();
            HttpSession first = rawSession(client);
            client.get(server.url("/json")).join();

            assertThat(rawSession(client)).isSameAs(first);
            client.close().join();
        }
    }

    @Test
    void acquireSessionClosedClientRaises() {
        HttpClient client = new HttpClient(new SessionConfig(), true);
        client.close().join();

        CompletionException exception = assertCompletionException(() -> client.get("http://example.com").join());

        assertThat(rootCause(exception)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HttpClient is closed");
    }

    @Test
    void releaseSessionReusableDoesNotImmediatelyCloseCachedSession() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), true);

            client.get(server.url("/json")).join();

            assertThat(rawSession(client).isClosed()).isFalse();
            client.close().join();
        }
    }

    @Test
    void releaseSessionNonReusableClosesSessionAfterRequest() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            SessionConfig config = new SessionConfig(mapOf("headers", Map.of("Case", "non-reuse")));
            HttpClient client = new HttpClient(config, false);

            client.get(server.url("/json")).join();

            assertThat(HttpSessionManager.getInstance().getStats().join().get("total_resources")).isEqualTo(0);
        }
    }

    @Test
    void closeReusableClientReleasesSession() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), true);
            client.get(server.url("/json")).join();

            client.close().join();

            assertThat(client.isClosed()).isTrue();
            assertThat(HttpSessionManager.getInstance().getStats().join().get("total_resources")).isEqualTo(0);
        }
    }

    @Test
    void buildRequestKwargsContainsHeadersKey() {
        HttpClient client = new HttpClient(new SessionConfig(mapOf("headers", Map.of("User-Agent", "Test"))));

        assertThat(client.buildRequestKwargs(null, null, null, Map.of())).containsKey("headers");
    }

    @Test
    void buildRequestKwargsMergesCustomHeaders() {
        HttpClient client = new HttpClient(new SessionConfig(mapOf("headers", Map.of("User-Agent", "Test"))));

        Map<String, Object> kwargs = client.buildRequestKwargs(Map.of("X-Custom", "value"), null, null, Map.of());

        Map<?, ?> headers = (Map<?, ?>) kwargs.get("headers");
        assertThat(headers.get("X-Custom")).isEqualTo("value");
        assertThat(headers.get("User-Agent")).isEqualTo("Test");
    }

    @Test
    void buildRequestKwargsUsesTimeoutValue() {
        HttpClient client = new HttpClient(new SessionConfig());

        Map<String, Object> kwargs = client.buildRequestKwargs(null, 10.0d, null, Map.of());

        Map<?, ?> timeout = (Map<?, ?>) kwargs.get("timeout");
        assertThat(timeout.get("total")).isEqualTo(10.0d);
    }

    @Test
    void buildRequestKwargsUsesTimeoutArgs() {
        HttpClient client = new HttpClient(new SessionConfig());

        Map<String, Object> kwargs = client.buildRequestKwargs(null, null,
                Map.of("total", 20.0d, "connect", 5.0d), Map.of());

        Map<?, ?> timeout = (Map<?, ?>) kwargs.get("timeout");
        assertThat(timeout.get("total")).isEqualTo(20.0d);
        assertThat(timeout.get("connect")).isEqualTo(5.0d);
    }

    @Test
    void requestSuccessReturnsResponseMap() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            Map<String, Object> result = client.get(server.url("/json")).join();

            assertThat(result).containsEntry("code", 200);
            assertThat(((Map<?, ?>) result.get("data")).get("data")).isEqualTo("test");
            assertThat(result.get("url").toString()).contains("/json");
            assertThat(result).containsEntry("reason", "OK");
        }
    }

    @Test
    void requestWithChunkedUsesParser() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            Map<String, Object> result = client.get(server.url("/text"), null,
                    HttpClient.RequestOptions.defaults().withChunking(true, 1024, null)).join();

            assertThat(result).containsEntry("code", 200);
            assertThat(result).containsEntry("data", "Hello World");
        }
    }

    @Test
    void requestWithSizeLimitRaisesForLargeChunkedResponse() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            CompletionException exception = assertCompletionException(() -> client.get(server.url("/large"), null,
                    HttpClient.RequestOptions.defaults()
                            .withChunking(true, 1024, null)
                            .withResponseBytesSizeLimit(10)).join());

            assertThat(rootCause(exception)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Response too large");
        }
    }

    @Test
    void streamRequestReturnsChunks() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            List<Object> chunks = client.streamGet(server.url("/chunks"),
                    HttpClient.RequestOptions.defaults().withChunking(true, 6, null)).join();

            assertThat(chunks).hasSize(2);
            assertThat((byte[]) chunks.get(0)).isEqualTo("chunk1".getBytes(StandardCharsets.UTF_8));
            assertThat((byte[]) chunks.get(1)).isEqualTo("chunk2".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void streamRequestWithCallbackTransformsChunks() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);
            Function<byte[], Object> callback = data -> new String(data, StandardCharsets.UTF_8)
                    .toUpperCase()
                    .getBytes(StandardCharsets.UTF_8);

            List<Object> chunks = client.streamGet(server.url("/chunks"),
                    HttpClient.RequestOptions.defaults().withChunking(true, 6, callback)).join();

            assertThat((byte[]) chunks.get(0)).isEqualTo("CHUNK1".getBytes(StandardCharsets.UTF_8));
            assertThat((byte[]) chunks.get(1)).isEqualTo("CHUNK2".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void streamRequestWithAsyncEquivalentCallbackTransformsChunks() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);
            Function<byte[], Object> callback = data -> new String(data, StandardCharsets.UTF_8)
                    .toUpperCase()
                    .getBytes(StandardCharsets.UTF_8);

            List<Object> chunks = client.streamGet(server.url("/chunks"),
                    HttpClient.RequestOptions.defaults().withChunking(true, 6, callback)).join();

            assertThat((byte[]) chunks.get(0)).isEqualTo("CHUNK1".getBytes(StandardCharsets.UTF_8));
            assertThat((byte[]) chunks.get(1)).isEqualTo("CHUNK2".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void httpMethodsAllReturnSuccessfulResponse() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            assertThat(client.get(server.url("/echo"), Map.of("q", "test"), HttpClient.RequestOptions.defaults())
                    .join()).containsEntry("code", 200);
            assertThat(client.post(server.url("/echo"), Map.of("key", "value")).join()).containsEntry("code", 200);
            assertThat(client.put(server.url("/echo"), Map.of("key", "value")).join()).containsEntry("code", 200);
            assertThat(client.delete(server.url("/echo")).join()).containsEntry("code", 200);
            assertThat(client.patch(server.url("/echo"), Map.of("key", "value")).join()).containsEntry("code", 200);
            assertThat(client.head(server.url("/echo")).join()).containsEntry("code", 200);
            assertThat(client.options(server.url("/echo")).join()).containsEntry("code", 200);
        }
    }

    @Test
    void isClosedReflectsCloseState() {
        HttpClient client = new HttpClient(new SessionConfig());

        assertThat(client.isClosed()).isFalse();
        client.close().join();

        assertThat(client.isClosed()).isTrue();
    }

    @Test
    void integrationRealRequestMockUsesLocalServer() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            Map<String, Object> result = client.get(server.url("/success")).join();

            assertThat(result).containsEntry("code", 200);
            assertThat(((Map<?, ?>) result.get("data")).get("success")).isEqualTo(true);
        }
    }

    @Test
    void errorHandlingPropagatesConnectionError() {
        String url;
        try (LocalHttpServer server = LocalHttpServer.start()) {
            url = server.url("/json");
        }
        HttpClient client = new HttpClient(new SessionConfig(mapOf("connect_timeout", 0.05d)), false);

        CompletionException exception = assertCompletionException(() -> client.get(url).join());

        assertThat(rootCause(exception)).isNotNull();
    }

    @Test
    void requestWithHttpErrorReturnsStatusAndReason() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            Map<String, Object> result = client.get(server.url("/error")).join();

            assertThat(result).containsEntry("code", 404);
            assertThat(result).containsEntry("data", "Not Found");
            assertThat(result).containsEntry("reason", "Not Found");
        }
    }

    @Test
    void requestWithTimeoutPropagatesTimeout() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            CompletionException exception = assertCompletionException(() -> client.get(server.url("/slow"), null,
                    HttpClient.RequestOptions.defaults().withTimeout(0.01d)).join());

            Throwable cause = rootCause(exception);
            assertThat(cause).isInstanceOfAny(HttpTimeoutException.class, java.net.ConnectException.class);
            assertThat(cause.getMessage()).containsIgnoringCase("timed out");
        }
    }

    @Test
    void differentContentTypesReturnMatchingDataTypes() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            Map<String, Object> json = client.get(server.url("/json")).join();
            Map<String, Object> text = client.get(server.url("/text")).join();
            Map<String, Object> binary = client.get(server.url("/binary")).join();

            assertThat(((Map<?, ?>) json.get("data")).get("data")).isEqualTo("test");
            assertThat(text).containsEntry("data", "Hello World");
            assertThat((byte[]) binary.get("data")).isEqualTo("binary data".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void sessionReleaseOnErrorForNonReusableClient() {
        String url;
        try (LocalHttpServer server = LocalHttpServer.start()) {
            url = server.url("/json");
        }
        HttpClient client = new HttpClient(new SessionConfig(mapOf("connect_timeout", 0.05d)), false);

        assertCompletionException(() -> client.get(url).join());

        assertThat(HttpSessionManager.getInstance().getStats().join().get("total_resources")).isEqualTo(0);
    }

    @Test
    void concurrentRequestsReturnAllResponses() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), true);
            List<CompletableFuture<Map<String, Object>>> futures = IntStream.range(0, 5)
                    .<CompletableFuture<Map<String, Object>>>mapToObj(index -> client.get(server.url("/success")))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            assertThat(futures).hasSize(5);
            assertThat(futures.stream().map(future -> future.join().get("code")).toList())
                    .containsExactly(200, 200, 200, 200, 200);
            client.close().join();
        }
    }

    @Test
    void customHeadersAndParamsReachServer() {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            HttpClient client = new HttpClient(new SessionConfig(), false);

            client.get(server.url("/echo"), Map.of("q", "test"),
                    HttpClient.RequestOptions.defaults().withHeaders(Map.of("X-Custom", "value"))).join();

            assertThat(server.lastRequest.method()).isEqualTo("GET");
            assertThat(server.lastRequest.query()).isEqualTo("q=test");
            assertThat(server.lastRequest.header("X-Custom")).isEqualTo("value");
        }
    }

    private static HttpSession rawSession(HttpClient client) throws Exception {
        Field field = HttpClient.class.getDeclaredField("session");
        field.setAccessible(true);
        return (HttpSession) field.get(client);
    }

    private static CompletionException assertCompletionException(Runnable action) {
        try {
            action.run();
        } catch (CompletionException exception) {
            return exception;
        }
        throw new AssertionError("Expected CompletionException");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    /**
     * Mirrors Python's mocked aiohttp session/response fixtures in
     * {@code tests/unit_tests/core/common/clients/test_http_client.py}.
     */
    private static final class LocalHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private volatile RequestRecord lastRequest = new RequestRecord("", "", new com.sun.net.httpserver.Headers());

        private LocalHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        private static LocalHttpServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                ExecutorService executor = Executors.newCachedThreadPool();
                LocalHttpServer wrapper = new LocalHttpServer(server, executor);
                server.createContext("/", wrapper::handle);
                server.setExecutor(executor);
                server.start();
                return wrapper;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to start local HTTP server", exception);
            }
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private void handle(HttpExchange exchange) throws IOException {
            lastRequest = new RequestRecord(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders()
            );
            String path = exchange.getRequestURI().getPath();
            if ("/slow".equals(path)) {
                sleep();
                respond(exchange, 200, "application/json", "{\"data\":\"slow\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/error".equals(path)) {
                respond(exchange, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/success".equals(path)) {
                respond(exchange, 200, "application/json", "{\"success\":true}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/text".equals(path)) {
                respond(exchange, 200, "text/plain", "Hello World".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/binary".equals(path)) {
                respond(exchange, 200, "application/octet-stream", "binary data".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/large".equals(path)) {
                byte[] body = new byte[1024];
                Arrays.fill(body, (byte) 'x');
                respond(exchange, 200, "application/octet-stream", body);
                return;
            }
            if ("/chunks".equals(path)) {
                respond(exchange, 200, "text/plain", "chunk1chunk2".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, "application/json", "{\"data\":\"test\"}".getBytes(StandardCharsets.UTF_8));
        }

        private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
                throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private static void sleep() {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private record RequestRecord(String method, String query, com.sun.net.httpserver.Headers headers) {
        private String header(String name) {
            return headers.getFirst(name);
        }
    }
}
