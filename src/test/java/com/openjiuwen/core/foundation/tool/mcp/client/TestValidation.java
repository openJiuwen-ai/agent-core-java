/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for tool validation.
 * <p>
 * Mirrors Python's {@code test_validation.py} from
 * {@code tests/unit_tests/core/foundation/tool/test_validation.py}.
 */
@DisplayName("Tool Validation Tests")
class TestValidation {

    @Nested
    @DisplayName("Tool Validation Tests")
    class TestToolValidationClass {

        private String previousSsrfProtectEnabled;

        @BeforeEach
        void setUp() {
            previousSsrfProtectEnabled = System.getProperty("SSRF_PROTECT_ENABLED");
            System.setProperty("SSRF_PROTECT_ENABLED", "false");
        }

        @AfterEach
        void tearDown() {
            if (previousSsrfProtectEnabled == null) {
                System.clearProperty("SSRF_PROTECT_ENABLED");
            } else {
                System.setProperty("SSRF_PROTECT_ENABLED", previousSsrfProtectEnabled);
            }
        }

        @Test
        @Tag("level0")
        @DisplayName("sse client auth validation")
        void testSseClientAuthValidation() throws Exception {
            McpServerConfig serverConfig = McpServerConfig.builder()
                    .serverName("test-sse-server")
                    .serverPath("http://127.0.0.1:8080/sse")
                    .clientType("sse")
                    .authHeaders(Map.of("Authorization", "Bearer test_token", "X-Custom-Header", "test_value"))
                    .authQueryParams(Map.of("api_key", "test_key", "version", "v1"))
                    .serverId("test-sse-server-id")
                    .build();

            SseClient sseClient = new SseClient(serverConfig);
            McpServerConfig actualConfig = readConfig(sseClient, SseClient.class);

            assertEquals("sse", actualConfig.getClientType());
            assertEquals("Bearer test_token", actualConfig.getAuthHeaders().get("Authorization"));
            assertEquals("test_value", actualConfig.getAuthHeaders().get("X-Custom-Header"));
            assertEquals("test_key", actualConfig.getAuthQueryParams().get("api_key"));
            assertEquals("v1", actualConfig.getAuthQueryParams().get("version"));
        }

        @Test
        @Tag("level0")
        @DisplayName("streamable http client auth validation")
        void testStreamableHttpClientAuthValidation() throws Exception {
            StreamableHttpClient client = new StreamableHttpClient(
                    "http://127.0.0.1:8080/streamable",
                    "test-streamable-server",
                    Map.of("Authorization", "Bearer test_token", "X-Custom-Header", "test_value"),
                    Map.of("api_key", "test_key", "version", "v1"));

            McpServerConfig actualConfig = readConfig(client, StreamableHttpClient.class);

            assertEquals("streamable-http", actualConfig.getClientType());
            assertEquals("Bearer test_token", actualConfig.getAuthHeaders().get("Authorization"));
            assertEquals("test_key", actualConfig.getAuthQueryParams().get("api_key"));
            assertEquals("v1", actualConfig.getAuthQueryParams().get("version"));
        }

        @Test
        @Tag("level0")
        @DisplayName("restful api card validation")
        void testRestfulApiCardValidation() {
            RestfulApiCard validCard = RestfulApiCard.builder()
                    .name("test_api")
                    .description("test")
                    .url("https://example.com/users/{id}")
                    .method("GET")
                    .headers(Map.of("Content-Type", "application/json"))
                    .queries(Map.of("page", 1, "limit", 10))
                    .timeout(30.0)
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of(
                                    "type", "integer",
                                    "description", "User ID",
                                    "location", "path")),
                            "required", java.util.List.of("id")))
                    .build();

            assertDoesNotThrow(() -> new RestfulApi(validCard));
            assertEquals("GET", validCard.getMethod());
            assertEquals("https://example.com/users/{id}", validCard.getUrl());

            RestfulApiCard invalidMethod = RestfulApiCard.builder()
                    .name("test_api")
                    .description("test")
                    .url("https://example.com/users")
                    .method("INVALID_METHOD")
                    .build();
            assertThrows(Throwable.class, () -> new RestfulApi(invalidMethod));

            RestfulApiCard invalidPath = RestfulApiCard.builder()
                    .name("test_api")
                    .description("test")
                    .url("https://example.com/users/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of("type", "integer"))))
                    .build();
            assertThrows(Throwable.class, () -> new RestfulApi(invalidPath));
        }

        @Test
        @Tag("level0")
        @DisplayName("restful api auth validation")
        void testRestfulApiAuthValidation() throws Exception {
            AtomicReference<String> requestHeader = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                writeJson(exchange, 200, "{\"id\":1,\"name\":\"test\"}");
            });
            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("test_api")
                        .description("test")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/users/{id}")
                        .method("GET")
                        .headers(Map.of("Content-Type", "application/json"))
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("id", Map.of(
                                        "type", "integer",
                                        "description", "User ID",
                                        "location", "path")),
                                "required", java.util.List.of("id")))
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of("id", 1));

                assertEquals(200, result.get("code"));
                assertEquals("application/json", requestHeader.get());
            } finally {
                server.stop(0);
            }
        }

        @Test
        @Tag("level0")
        @DisplayName("auth header and query provider")
        void testAuthHeaderAndQueryProvider() throws Exception {
            StreamableHttpClient client = new StreamableHttpClient(
                    "https://example.com/sse?existing=1",
                    "test-server",
                    Map.of("Authorization", "Bearer x"),
                    Map.of("ak", "demo-ak"));

            McpServerConfig config = readConfig(client, StreamableHttpClient.class);

            assertEquals("Bearer x", config.getAuthHeaders().get("Authorization"));
            assertEquals("demo-ak", config.getAuthQueryParams().get("ak"));
            assertEquals("https://example.com/sse?existing=1", config.getServerPath());
        }

        @Test
        @Tag("level0")
        @DisplayName("restful api parameter mapping")
        void testRestfulApiParameterMapping() throws Exception {
            AtomicReference<String> requestPath = new AtomicReference<>();
            AtomicReference<Map<String, String>> requestQuery = new AtomicReference<>();
            AtomicReference<String> requestHeader = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestPath.set(exchange.getRequestURI().getPath());
                requestQuery.set(parseQuery(exchange.getRequestURI().getRawQuery()));
                requestHeader.set(exchange.getRequestHeaders().getFirst("custom_header"));
                writeJson(exchange, 200, "[]");
            });
            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("test_api")
                        .description("test")
                        .url("http://127.0.0.1:" + server.getAddress().getPort()
                                + "/users/{id}/posts/{post_id}")
                        .method("GET")
                        .headers(Map.of("Content-Type", "application/json"))
                        .queries(Map.of("default_param", "default_value"))
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "location", "path"),
                                        "post_id", Map.of("type", "integer", "location", "path"),
                                        "page", Map.of("type", "integer", "location", "query"),
                                        "limit", Map.of("type", "integer", "location", "query"),
                                        "custom_header", Map.of("type", "string", "location", "header")),
                                "required", java.util.List.of("id", "post_id")))
                        .build());

                Object result = api.invoke(Map.of(
                        "id", 1,
                        "post_id", 10,
                        "page", 1,
                        "limit", 10,
                        "custom_header", "test_header_value"));

                assertNotNull(result);
                assertEquals("/users/1/posts/10", requestPath.get());
                assertEquals("default_value", requestQuery.get().get("default_param"));
                assertEquals("1", requestQuery.get().get("page"));
                assertEquals("10", requestQuery.get().get("limit"));
                assertEquals("test_header_value", requestHeader.get());
            } finally {
                server.stop(0);
            }
        }

        private <T> McpServerConfig readConfig(T client, Class<?> clientClass) throws Exception {
            Field configField = clientClass.getDeclaredField("config");
            configField.setAccessible(true);
            return (McpServerConfig) configField.get(client);
        }

        private HttpServer createServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler);
            server.start();
            return server;
        }

        private void writeJson(HttpExchange exchange, int statusCode, String payload) throws IOException {
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }

        private Map<String, String> parseQuery(String rawQuery) {
            Map<String, String> result = new LinkedHashMap<>();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return result;
            }
            for (String pair : rawQuery.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length > 1
                        ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                        : "";
                result.put(key, value);
            }
            return result;
        }
    }
}
