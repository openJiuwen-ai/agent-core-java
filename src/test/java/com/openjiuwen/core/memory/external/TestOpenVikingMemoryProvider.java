/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OpenVikingMemoryProvider.
 * <p>
 * Mirrors Python's {@code test_openviking_memory_provider.py} in
 * {@code tests.unit_tests.core.memory.external}.
 */
@DisplayName("OpenViking Memory Provider Tests")
class TestOpenVikingMemoryProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("Name And Availability")
    class TestNameAndAvailability {
        @Test
        void testNameReturnsOpenviking() {
            OpenVikingMemoryProvider provider = provider();

            assertEquals("openviking", provider.name());
        }

        @Test
        void testIsAvailableWithEndpoint() {
            OpenVikingMemoryProvider provider = provider();

            assertTrue(provider.isAvailable());
        }

        @Test
        void testIsAvailableWithoutEndpoint() {
            OpenVikingMemoryProvider provider = provider("", "");

            assertFalse(provider.isAvailable());
        }

        @Test
        void testIsAvailableWithEmptyEndpoint() {
            OpenVikingMemoryProvider provider = provider("", "");

            assertFalse(provider.isAvailable());
        }

        @Test
        void testIsAvailableReadsEnvVar() {
            OpenVikingMemoryProvider provider = providerWithEnv(Map.of(
                    "OPENVIKING_ENDPOINT", "http://env-host:9090"));

            assertTrue(provider.isAvailable());
            assertEquals("http://env-host:9090", field(provider, "endpoint"));
        }
    }

    @Nested
    @DisplayName("Is Initialized")
    class TestIsInitialized {
        @Test
        void testNotInitializedByDefault() {
            OpenVikingMemoryProvider provider = provider();

            assertFalse(provider.isInitialized());
        }

        @Test
        void testInitializedAfterInitialize() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                OpenVikingMemoryProvider provider = provider(server.url());

                await(provider.initialize(Map.of("session_id", "sess-1")));

                assertTrue(provider.isInitialized());
            }
        }

        @Test
        void testNotInitializedIfHealthCheckFails() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                server.healthStatus(503);
                OpenVikingMemoryProvider provider = provider(server.url());

                await(provider.initialize(Map.of()));

                assertFalse(provider.isInitialized());
            }
        }

        @Test
        void testShutdownResetsInitialized() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                assertTrue(ctx.provider.isInitialized());

                await(ctx.provider.shutdown());

                assertFalse(ctx.provider.isInitialized());
            }
        }
    }

    @Nested
    @DisplayName("Initialize")
    class TestInitialize {
        @Test
        void testInitializeCreatesClientAndChecksHealth() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                OpenVikingMemoryProvider provider = provider(server.url());

                await(provider.initialize(Map.of("session_id", "sess-1")));

                RecordedRequest health = server.requests().get(0);
                assertEquals("GET", health.method());
                assertEquals("/health", health.path());
                assertEquals("default", health.header("X-OpenViking-Account"));
                assertEquals("default", health.header("X-OpenViking-User"));
                assertEquals("hermes", health.header("X-OpenViking-Agent"));
                assertEquals("test-key", health.header("X-API-Key"));
            }
        }

        @Test
        void testInitializeSetsSessionId() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                OpenVikingMemoryProvider provider = provider(server.url());

                await(provider.initialize(Map.of("session_id", "my-session")));

                assertEquals("my-session", field(provider, "sessionId"));
            }
        }

        @Test
        void testInitializeSetsEmptySessionIdByDefault() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                OpenVikingMemoryProvider provider = provider(server.url());

                await(provider.initialize(Map.of()));

                assertEquals("", field(provider, "sessionId"));
            }
        }

        @Test
        void testInitializeHandlesImportError() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(
                    "http://localhost:8080", "", "", "", "", Map.of(),
                    () -> {
                        throw new RuntimeException("no httpx");
                    });

            await(provider.initialize(Map.of()));

            assertFalse(provider.isInitialized());
        }

        @Test
        void testInitializeHandlesGeneralException() throws Exception {
            HttpClient client = mock(HttpClient.class);
            stubSendFailure(client, new IOException("connection refused"));
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(
                    "http://localhost:8080", "", "", "", "", Map.of(), () -> client);

            await(provider.initialize(Map.of()));

            assertFalse(provider.isInitialized());
        }

        @Test
        void testInitializeUsesEnvVarsForAccountAndUser() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                OpenVikingMemoryProvider provider = providerWithEnv(Map.of(
                        "OPENVIKING_ENDPOINT", server.url(),
                        "OPENVIKING_ACCOUNT", "myacct",
                        "OPENVIKING_USER", "myuser"));

                await(provider.initialize(Map.of()));

                RecordedRequest health = server.requests().get(0);
                assertEquals("myacct", health.header("X-OpenViking-Account"));
                assertEquals("myuser", health.header("X-OpenViking-User"));
                assertEquals("hermes", health.header("X-OpenViking-Agent"));
                assertEquals(Optional.empty(), health.headerValue("X-API-Key"));
            }
        }
    }

    @Nested
    @DisplayName("System Prompt Block")
    class TestSystemPromptBlock {
        @Test
        void testReturnsNonEmptyString() {
            OpenVikingMemoryProvider provider = provider();

            String prompt = provider.systemPromptBlock();

            assertNotNull(prompt);
            assertFalse(prompt.isEmpty());
            assertTrue(prompt.contains("viking_search"));
            assertTrue(prompt.contains("viking_read"));
            assertTrue(prompt.contains("viking_browse"));
            assertTrue(prompt.contains("viking_remember"));
            assertTrue(prompt.contains("viking_add_resource"));
        }
    }

    @Nested
    @DisplayName("Get Tool Schemas")
    class TestGetToolSchemas {
        @Test
        void testReturnsFiveSchemas() {
            OpenVikingMemoryProvider provider = provider();

            List<Map<String, Object>> schemas = provider.getToolSchemas();
            List<String> names = schemas.stream()
                    .map(schema -> String.valueOf(schema.get("name")))
                    .toList();

            assertEquals(5, schemas.size());
            assertEquals(List.of(
                    "viking_search",
                    "viking_read",
                    "viking_browse",
                    "viking_remember",
                    "viking_add_resource"), names);
        }

        @Test
        void testVikingSearchSchemaStructure() {
            Map<String, Object> schema = schema("viking_search");

            assertEquals("viking_search", schema.get("name"));
            assertTrue(properties(schema).containsKey("query"));
            assertTrue(properties(schema).containsKey("mode"));
            assertTrue(properties(schema).containsKey("top_k"));
            assertEquals(List.of("query"), required(schema));
        }

        @Test
        void testVikingReadSchemaStructure() {
            Map<String, Object> schema = schema("viking_read");

            assertEquals("viking_read", schema.get("name"));
            assertTrue(properties(schema).containsKey("uri"));
            assertTrue(properties(schema).containsKey("detail"));
            assertEquals(List.of("uri"), required(schema));
        }

        @Test
        void testVikingBrowseSchemaStructure() {
            Map<String, Object> schema = schema("viking_browse");

            assertEquals("viking_browse", schema.get("name"));
            assertTrue(properties(schema).containsKey("action"));
            assertTrue(properties(schema).containsKey("path"));
            assertEquals(List.of("action"), required(schema));
        }

        @Test
        void testVikingRememberSchemaStructure() {
            Map<String, Object> schema = schema("viking_remember");

            assertEquals("viking_remember", schema.get("name"));
            assertTrue(properties(schema).containsKey("content"));
            assertTrue(properties(schema).containsKey("category"));
            assertEquals(List.of("content"), required(schema));
        }

        @Test
        void testVikingAddResourceSchemaStructure() {
            Map<String, Object> schema = schema("viking_add_resource");

            assertEquals("viking_add_resource", schema.get("name"));
            assertTrue(properties(schema).containsKey("url"));
            assertTrue(properties(schema).containsKey("title"));
            assertEquals(List.of("url"), required(schema));
        }
    }

    @Nested
    @DisplayName("Handle Tool Call")
    class TestHandleToolCall {
        @Test
        void testNotInitializedReturnsError() {
            OpenVikingMemoryProvider provider = provider("http://localhost:8080", "");

            Map<String, Object> parsed = parse(await(provider.handleToolCall(
                    "viking_search", Map.of("query", "test"))));

            assertTrue(parsed.containsKey("error"));
            assertTrue(String.valueOf(parsed.get("error")).contains("not connected"));
        }

        @Test
        void testUnknownToolReturnsError() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall("unknown_tool", Map.of())));

                assertTrue(parsed.containsKey("error"));
                assertTrue(String.valueOf(parsed.get("error")).contains("unknown_tool"));
            }
        }

        @Test
        void testVikingSearch() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of(
                        "memories", List.of(Map.of(
                                "uri", "viking://mem/1", "score", 0.9, "abstract", "Python Guide")),
                        "resources", List.of(Map.of(
                                "uri", "viking://res/1", "score", 0.8, "abstract", "Rust Book")))));

                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall(
                        "viking_search", Map.of("query", "programming", "mode", "deep", "limit", 5))));

                assertEquals(2, list(parsed.get("results")).size());
                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("POST", request.method());
                assertEquals("/api/v1/search/find", request.path());
                assertEquals(Map.of("query", "programming", "mode", "deep", "top_k", 5), request.jsonBody());
            }
        }

        @Test
        void testVikingSearchWithDefaults() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of()));

                await(ctx.provider.handleToolCall("viking_search", Map.of("query", "test")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/search/find", request.path());
                assertEquals(Map.of("query", "test"), request.jsonBody());
            }
        }

        @Test
        void testVikingReadOverview() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", "Overview text"));

                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall(
                        "viking_read", Map.of("uri", "viking://doc/1"))));

                assertEquals("Overview text", parsed.get("content"));
                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/content/overview", request.path());
                assertEquals(Map.of("uri", "viking://doc/1"), request.queryParams());
            }
        }

        @Test
        void testVikingReadAbstract() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", "Abstract text"));

                await(ctx.provider.handleToolCall(
                        "viking_read", Map.of("uri", "viking://doc/1", "detail", "abstract")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/content/abstract", request.path());
                assertEquals(Map.of("uri", "viking://doc/1"), request.queryParams());
            }
        }

        @Test
        void testVikingReadFull() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", "Full text"));

                await(ctx.provider.handleToolCall(
                        "viking_read", Map.of("uri", "viking://doc/1", "detail", "full")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/content/read", request.path());
                assertEquals(Map.of("uri", "viking://doc/1"), request.queryParams());
            }
        }

        @Test
        void testVikingBrowseList() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", List.of(Map.of(
                        "name", "file1.md", "uri", "viking://file1.md", "isDir", false))));

                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall(
                        "viking_browse", Map.of("action", "list", "path", "viking://docs"))));

                assertTrue(parsed.containsKey("entries"));
                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/fs/ls", request.path());
                assertEquals(Map.of("uri", "viking://docs"), request.queryParams());
            }
        }

        @Test
        void testVikingBrowseTree() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", List.of()));

                await(ctx.provider.handleToolCall("viking_browse", Map.of("action", "tree")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/fs/tree", request.path());
                assertEquals(Map.of("uri", "viking://"), request.queryParams());
            }
        }

        @Test
        void testVikingBrowseStat() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of("size", 1024)));

                await(ctx.provider.handleToolCall(
                        "viking_browse", Map.of("action", "stat", "path", "viking://docs/file.md")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/fs/stat", request.path());
                assertEquals(Map.of("uri", "viking://docs/file.md"), request.queryParams());
            }
        }

        @Test
        void testVikingRemember() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall(
                        "viking_remember",
                        Map.of("content", "User prefers dark mode", "category", "preference"))));

                assertEquals("stored", parsed.get("status"));
                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/sessions/sess-123/messages", request.path());
                Map<String, Object> body = request.jsonBody();
                assertEquals("user", body.get("role"));
                Map<?, ?> part = (Map<?, ?>) list(body.get("parts")).get(0);
                String text = String.valueOf(part.get("text"));
                assertEquals("text", part.get("type"));
                assertTrue(text.startsWith("[Remember "));
                assertTrue(text.contains("preference"));
                assertTrue(text.endsWith("User prefers dark mode"));
            }
        }

        @Test
        void testVikingRememberDefaultCategory() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                await(ctx.provider.handleToolCall("viking_remember", Map.of("content", "some fact")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/sessions/sess-123/messages", request.path());
                Map<String, Object> body = request.jsonBody();
                assertEquals("user", body.get("role"));
                Map<?, ?> part = (Map<?, ?>) list(body.get("parts")).get(0);
                assertEquals(Map.of("type", "text", "text", "[Remember] some fact"), part);
            }
        }

        @Test
        void testVikingAddResource() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of("root_uri", "viking://res/docs")));

                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall(
                        "viking_add_resource",
                        Map.of("url", "https://example.com/docs", "reason", "Useful docs"))));

                assertEquals("added", parsed.get("status"));
                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/resources", request.path());
                assertEquals(Map.of("path", "https://example.com/docs", "reason", "Useful docs"),
                        request.jsonBody());
            }
        }

        @Test
        void testVikingAddResourceDefaultTitle() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of()));

                await(ctx.provider.handleToolCall(
                        "viking_add_resource", Map.of("url", "https://example.com")));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/resources", request.path());
                assertEquals(Map.of("path", "https://example.com"), request.jsonBody());
            }
        }

        @Test
        void testHandleToolCallExceptionReturnsError() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueue(500, "network error");

                Map<String, Object> parsed = parse(await(ctx.provider.handleToolCall(
                        "viking_search", Map.of("query", "test"))));

                assertTrue(parsed.containsKey("error"));
                assertTrue(String.valueOf(parsed.get("error")).contains("network error"));
            }
        }
    }

    @Nested
    @DisplayName("Prefetch")
    class TestPrefetch {
        @Test
        void testNotInitializedReturnsEmpty() {
            OpenVikingMemoryProvider provider = provider("http://localhost:8080", "");

            String result = await(provider.prefetch("test query", Map.of()));

            assertEquals("", result);
        }

        @Test
        void testEmptyQueryReturnsEmpty() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                String result = await(ctx.provider.prefetch("", Map.of()));

                assertEquals("", result);
            }
        }

        @Test
        void testPrefetchWithResults() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of(
                        "memories", List.of(Map.of(
                                "uri", "viking://mem/1", "abstract", "Memory A", "score", 0.9)),
                        "resources", List.of(Map.of(
                                "uri", "viking://res/1", "abstract", "Resource B", "score", 0.8)))));

                String result = await(ctx.provider.prefetch("python", Map.of()));

                assertTrue(result.contains("## OpenViking Context"));
                assertTrue(result.contains("Memory A"));
                assertTrue(result.contains("Resource B"));
                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/search/find", request.path());
                assertEquals(Map.of("query", "python", "top_k", 5), request.jsonBody());
            }
        }

        @Test
        void testPrefetchNoResultsReturnsEmpty() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of()));

                String result = await(ctx.provider.prefetch("nothing", Map.of()));

                assertEquals("", result);
            }
        }

        @Test
        void testPrefetchExceptionReturnsEmpty() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueue(500, "timeout");

                String result = await(ctx.provider.prefetch("test", Map.of()));

                assertEquals("", result);
            }
        }

        @Test
        void testPrefetchMissingAbstractSkipped() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueueJson(Map.of("result", Map.of(
                        "memories", List.of(Map.of("uri", "viking://mem/1", "score", 0.5)))));

                String result = await(ctx.provider.prefetch("test", Map.of()));

                assertEquals("", result);
            }
        }
    }

    @Nested
    @DisplayName("Sync Turn")
    class TestSyncTurn {
        @Test
        void testNotInitializedDoesNothing() {
            OpenVikingMemoryProvider provider = provider("http://localhost:8080", "");

            await(provider.syncTurn("hello", "hi", Map.of()));
        }

        @Test
        void testSyncTurnPostsMessages() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                await(ctx.provider.syncTurn("hello", "hi there", Map.of()));

                List<RecordedRequest> calls = ctx.server.nonHealthRequests();
                assertEquals(2, calls.size());
                assertEquals("/api/v1/sessions/sess-123/messages", calls.get(0).path());
                assertEquals(Map.of("role", "user", "content", "hello"), calls.get(0).jsonBody());
                assertEquals("/api/v1/sessions/sess-123/messages", calls.get(1).path());
                assertEquals(Map.of("role", "assistant", "content", "hi there"), calls.get(1).jsonBody());
            }
        }

        @Test
        void testSyncTurnUsesKwargsSessionId() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                await(ctx.provider.syncTurn("hello", "hi", Map.of("session_id", "custom-session")));

                List<RecordedRequest> calls = ctx.server.nonHealthRequests();
                assertEquals(2, calls.size());
                assertEquals("/api/v1/sessions/custom-session/messages", calls.get(0).path());
                assertEquals(Map.of("role", "user", "content", "hello"), calls.get(0).jsonBody());
                assertEquals("/api/v1/sessions/custom-session/messages", calls.get(1).path());
                assertEquals(Map.of("role", "assistant", "content", "hi"), calls.get(1).jsonBody());
            }
        }

        @Test
        void testSyncTurnExceptionIsSwallowed() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueue(500, "network error");

                assertDoesNotThrow(() -> await(ctx.provider.syncTurn("hello", "hi", Map.of())));
            }
        }
    }

    @Nested
    @DisplayName("On Session End")
    class TestOnSessionEnd {
        @Test
        void testNotInitializedDoesNothing() {
            OpenVikingMemoryProvider provider = provider("http://localhost:8080", "");

            await(provider.onSessionEnd(List.of()));
        }

        @Test
        void testNoSessionIdDoesNothing() throws Exception {
            try (MockOpenVikingServer server = new MockOpenVikingServer()) {
                OpenVikingMemoryProvider provider = provider(server.url());
                await(provider.initialize(Map.of()));
                server.clearRequests();

                await(provider.onSessionEnd(List.of(Map.of("role", "user", "content", "bye"))));

                assertTrue(server.nonHealthRequests().isEmpty());
            }
        }

        @Test
        void testSessionEndCommitsSession() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                await(ctx.provider.onSessionEnd(List.of(Map.of("role", "user", "content", "bye"))));

                RecordedRequest request = ctx.server.firstNonHealth();
                assertEquals("/api/v1/sessions/sess-123/commit", request.path());
                assertEquals(Map.of(), request.jsonBody());
            }
        }

        @Test
        void testSessionEndExceptionIsSwallowed() throws Exception {
            try (Initialized ctx = initializedProvider()) {
                ctx.server.enqueue(500, "commit failed");

                assertDoesNotThrow(() -> await(ctx.provider.onSessionEnd(List.of())));
            }
        }
    }

    @Nested
    @DisplayName("Shutdown")
    class TestShutdown {
        @Test
        void testShutdownClosesClient() throws Exception {
            HttpClient client = mock(HttpClient.class);
            stubSend(client, response(200, "{}"));
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(
                    "http://localhost:8080", "", "", "", "", Map.of(), () -> client);
            await(provider.initialize(Map.of("session_id", "sess-123")));

            await(provider.shutdown());

            verify(client).close();
            assertFalse(provider.isInitialized());
        }

        @Test
        void testShutdownWithoutClient() {
            OpenVikingMemoryProvider provider = provider("http://localhost:8080", "");

            await(provider.shutdown());

            assertFalse(provider.isInitialized());
        }

        @Test
        void testShutdownHandlesCloseException() throws Exception {
            HttpClient client = mock(HttpClient.class);
            stubSend(client, response(200, "{}"));
            doThrow(new RuntimeException("close error")).when(client).close();
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(
                    "http://localhost:8080", "", "", "", "", Map.of(), () -> client);
            await(provider.initialize(Map.of()));

            assertDoesNotThrow(() -> await(provider.shutdown()));
            assertFalse(provider.isInitialized());
        }
    }

    private static OpenVikingMemoryProvider provider() {
        return provider("http://localhost:8080", "test-key");
    }

    private static OpenVikingMemoryProvider provider(String endpoint) {
        return provider(endpoint, "test-key");
    }

    private static OpenVikingMemoryProvider provider(String endpoint, String apiKey) {
        return new OpenVikingMemoryProvider(endpoint, apiKey, "", "", "", Map.of(), defaultFactory());
    }

    private static OpenVikingMemoryProvider providerWithEnv(Map<String, String> env) {
        return new OpenVikingMemoryProvider("", "", "", "", "", env, defaultFactory());
    }

    private static OpenVikingMemoryProvider.HttpClientFactory defaultFactory() {
        return () -> HttpClient.newBuilder().build();
    }

    private static Initialized initializedProvider() throws Exception {
        MockOpenVikingServer server = new MockOpenVikingServer();
        OpenVikingMemoryProvider provider = provider(server.url());
        await(provider.initialize(Map.of("session_id", "sess-123")));
        server.clearRequests();
        return new Initialized(provider, server);
    }

    private static <T> T await(CompletableFuture<T> future) {
        return future.orTimeout(5, TimeUnit.SECONDS).join();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> schema(String name) {
        return provider().getToolSchemas().stream()
                .filter(schema -> name.equals(schema.get("name")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        Map<String, Object> params = (Map<String, Object>) schema.get("parameters");
        return (Map<String, Object>) params.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        Map<String, Object> params = (Map<String, Object>) schema.get("parameters");
        return (List<String>) params.get("required");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSend(HttpClient client, HttpResponse<String> response) throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) response);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSendFailure(HttpClient client, Exception failure) throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(failure);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private record Initialized(OpenVikingMemoryProvider provider, MockOpenVikingServer server) implements AutoCloseable {
        @Override
        public void close() {
            server.close();
        }
    }

    private record Response(int status, String body) {
    }

    private static final class RecordedRequest {
        private final String method;
        private final String path;
        private final String rawQuery;
        private final Headers headers;
        private final String body;

        private RecordedRequest(String method, String path, String rawQuery, Headers headers, String body) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.headers = headers;
            this.body = body;
        }

        String method() {
            return method;
        }

        String path() {
            return path;
        }

        Optional<String> headerValue(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream().findFirst().stream())
                    .findFirst();
        }

        String header(String name) {
            return headerValue(name).orElse("");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> jsonBody() {
            try {
                return body.isEmpty() ? Map.of() : MAPPER.readValue(body, Map.class);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        Map<String, String> queryParams() {
            if (rawQuery == null || rawQuery.isEmpty()) {
                return Map.of();
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (String part : rawQuery.split("&")) {
                int equals = part.indexOf('=');
                String key = equals >= 0 ? part.substring(0, equals) : part;
                String value = equals >= 0 ? part.substring(equals + 1) : "";
                params.put(decode(key), decode(value));
            }
            return params;
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }

    private static final class MockOpenVikingServer implements AutoCloseable {
        private final HttpServer server;
        private final List<RecordedRequest> requests = new ArrayList<>();
        private final Queue<Response> responses = new ArrayDeque<>();
        private int healthStatus = 200;

        private MockOpenVikingServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        synchronized void healthStatus(int status) {
            healthStatus = status;
        }

        synchronized void enqueueJson(Map<String, Object> body) {
            try {
                enqueue(200, MAPPER.writeValueAsString(body));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        synchronized void enqueue(int status, String body) {
            responses.add(new Response(status, body));
        }

        synchronized List<RecordedRequest> requests() {
            return List.copyOf(requests);
        }

        synchronized List<RecordedRequest> nonHealthRequests() {
            return requests.stream()
                    .filter(request -> !"/health".equals(request.path()))
                    .toList();
        }

        synchronized RecordedRequest firstNonHealth() {
            return nonHealthRequests().stream().findFirst().orElseThrow();
        }

        synchronized void clearRequests() {
            requests.clear();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Headers headerCopy = new Headers();
            exchange.getRequestHeaders().forEach((key, values) -> headerCopy.put(key, new ArrayList<>(values)));
            RecordedRequest request = new RecordedRequest(
                    exchange.getRequestMethod(), uri.getPath(), uri.getRawQuery(), headerCopy, body);

            Response response;
            synchronized (this) {
                requests.add(request);
                if ("/health".equals(uri.getPath())) {
                    response = new Response(healthStatus, "{}");
                } else {
                    response = responses.isEmpty() ? new Response(200, "{}") : responses.remove();
                }
            }

            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
