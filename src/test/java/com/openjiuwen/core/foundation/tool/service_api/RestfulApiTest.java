/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for RESTful API tools.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/foundation/tool/test_restfulapi.py} and
 * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.</p>
 */
class RestfulApiTest {

    @BeforeAll
    static void disableSsrfProtectionForLocalParityServers() throws Exception {
        setEnvReader(key -> "SSRF_PROTECT_ENABLED".equals(key) ? "false" : System.getenv(key));
    }

    @AfterAll
    static void resetUrlUtilsEnvReader() throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        method.setAccessible(true);
        method.invoke(null);
    }

    @Nested
    @DisplayName("RestfulApiCard tests")
    class RestfulApiCardTests {

        @Test
        @DisplayName("toolInfo returns the Python-compatible ToolInfo object")
        void testGetToolInfo() {
            Map<String, Object> inputParams = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "test", Map.of("description", "test", "type", "string", "default", "123")
                    ),
                    "required", List.of("test")
            );

            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .inputParams(inputParams)
                    .url("http://127.0.0.1:8000")
                    .method("GET")
                    .build();

            ToolInfo result = card.toolInfo();
            assertEquals("test", result.getName());
            assertEquals("test", result.getDescription());
            assertEquals("object", result.getParameters().get("type"));
        }

        @Test
        @DisplayName("default values match the Python card")
        void testDefaults() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .build();

            assertEquals("POST", card.getMethod());
            assertEquals(60.0, card.getTimeout());
            assertEquals(10 * 1024 * 1024, card.getMaxResponseByteSize());
            assertEquals(Map.of(), card.getHeaders());
            assertEquals(Map.of(), card.getQueries());
            assertEquals(Map.of(), card.getPaths());
        }

        @Test
        @DisplayName("method validation happens while building the card")
        void testInvalidMethodRejectedByCard() {
            assertThrows(Throwable.class, () -> RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("INVALID_METHOD")
                    .build());
        }

        @Test
        @DisplayName("path params require input schema entries with location=path")
        void testPathParamValidation() {
            assertThrows(Throwable.class, () -> RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of("type", "integer"))))
                    .build());
        }

        @Test
        @DisplayName("RestfulApiCard extends ToolCard")
        void testRestfulApiCardExtendsToolCard() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("GET")
                    .build();

            assertInstanceOf(ToolCard.class, card);
        }
    }

    @Nested
    @DisplayName("RestfulApi invoke tests")
    class RestfulApiInvokeTests {

        @Test
        @DisplayName("GET maps path, query, default query, header, and body params into the URL")
        void testInvokeGetWithParams() throws Exception {
            AtomicReference<String> requestPath = new AtomicReference<>();
            AtomicReference<Map<String, String>> requestQuery = new AtomicReference<>();
            AtomicReference<String> requestHeader = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestPath.set(exchange.getRequestURI().getPath());
                requestQuery.set(parseQuery(exchange.getRequestURI().getRawQuery()));
                requestHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
                writeJson(exchange, 200, "{\"ok\":true}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("test")
                        .description("test")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/users/{id}")
                        .method("GET")
                        .headers(Map.of("X-Test", "demo"))
                        .queries(Map.of("limit", 10))
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "location", "path"),
                                        "format", Map.of("type", "string", "location", "query"),
                                        "ids", Map.of("type", "array", "location", "query"),
                                        "keyword", Map.of("type", "string"))))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(
                        Map.of("id", 42, "format", "json", "ids", List.of(1, 2), "keyword", "hello"));

                assertEquals("/users/42", requestPath.get());
                assertEquals("10", requestQuery.get().get("limit"));
                assertEquals("json", requestQuery.get().get("format"));
                assertEquals("hello", requestQuery.get().get("keyword"));
                assertEquals("1,2", requestQuery.get().get("ids"));
                assertEquals("demo", requestHeader.get());
                assertEquals(200, result.get("code"));
                assertEquals("success", result.get("message"));
                assertEquals("OK", result.get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("PUT sends JSON body and replaces path parameters")
        void testInvokePutWithBodyAndPathParams() throws Exception {
            AtomicReference<String> requestMethod = new AtomicReference<>();
            AtomicReference<String> requestPath = new AtomicReference<>();
            AtomicReference<String> requestBody = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestMethod.set(exchange.getRequestMethod());
                requestPath.set(exchange.getRequestURI().getPath());
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"updated\":true}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("update_activity")
                        .description("update activity")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/Activities/{id}")
                        .method("PUT")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "location", "path"),
                                        "name", Map.of("type", "string", "location", "body"))))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of("id", 7, "name", "demo"));

                assertEquals("PUT", requestMethod.get());
                assertEquals("/Activities/7", requestPath.get());
                assertTrue(requestBody.get().contains("\"name\":\"demo\""));
                assertEquals(200, result.get("code"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("DELETE sends body parameters as query params")
        void testDeleteUsesParamsForBodyLocation() throws Exception {
            AtomicReference<Map<String, String>> requestQuery = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestQuery.set(parseQuery(exchange.getRequestURI().getRawQuery()));
                writeJson(exchange, 200, "{\"deleted\":true}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("delete")
                        .description("delete")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/items")
                        .method("DELETE")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("reason", Map.of("type", "string"))))
                        .timeout(5.0)
                        .build());

                api.invoke(Map.of("reason", "cleanup"));

                assertEquals("cleanup", requestQuery.get().get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("raise_for_status=false returns error response instead of throwing")
        void testInvokeWithRaiseForStatusFalse() throws Exception {
            HttpServer server = createServer(exchange -> writeJson(exchange, 404, "{\"error\":true}"));
            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("test")
                        .description("test")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/missing")
                        .method("GET")
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of(), Map.of("raise_for_status", false));

                assertEquals(404, result.get("code"));
                assertEquals("Not Found", result.get("message"));
                assertEquals("Not Found", result.get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("response byte limit is enforced before parsing")
        void testResponseSizeLimit() throws Exception {
            HttpServer server = createServer(exchange -> writePlain(exchange, 200, "x".repeat(2048)));
            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("large")
                        .description("large")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/large")
                        .method("GET")
                        .timeout(5.0)
                        .build());

                assertThrows(Throwable.class, () -> api.invoke(Map.of(), Map.of("max_response_byte_size", 1024)));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("form params remove caller Content-Type and send multipart body")
        void testFormSubmissionFlow() throws Exception {
            AtomicReference<String> contentType = new AtomicReference<>();
            AtomicReference<String> requestBody = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"status\":\"success\"}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("submit")
                        .description("submit")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/submit")
                        .method("POST")
                        .headers(Map.of("Content-Type", "application/json"))
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "field", Map.of(
                                                "type", "string",
                                                "location", "form",
                                                "form_handler_type", "default"),
                                        "metadata", Map.of("type", "object", "location", "body"))))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(
                        Map.of("field", "value", "metadata", Map.of("key", "value")));

                assertEquals(200, result.get("code"));
                assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
                assertTrue(requestBody.get().contains("name=\"field\""));
                assertTrue(requestBody.get().contains("value"));
                assertTrue(requestBody.get().contains("name=\"metadata\""));
                assertTrue(requestBody.get().contains("{\"key\":\"value\"}"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("stream is not supported")
        void testStreamNotSupported() {
            RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                    .name("test_api")
                    .description("Test API")
                    .url("http://example.com/api/test")
                    .method("GET")
                    .build());

            assertThrows(Throwable.class, () -> api.stream(Map.of()));
        }
    }

    @Nested
    @DisplayName("GUI helper tests")
    class GuiHelperTests {

        @Test
        @DisplayName("getParametersByLocation groups path, body, query, header, and form params")
        void testGetParametersByLocationHelper() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("update_activity")
                    .description("update")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("PUT")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "integer", "description", "Activity ID", "location", "path"),
                                    "name", Map.of("type", "string", "description", "Activity name", "location", "body"),
                                    "notify", Map.of("type", "boolean", "description", "Send notification", "location", "query"),
                                    "api_key", Map.of("type", "string", "description", "API Key", "location", "header"),
                                    "file", Map.of("type", "string", "description", "File data", "location", "form")),
                            "required", List.of("id", "name")))
                    .build();

            Map<String, List<Map<String, Object>>> params = RestfulApi.getParametersByLocation(card);

            assertEquals("id", params.get("path").getFirst().get("name"));
            assertEquals(true, params.get("path").getFirst().get("required"));
            assertEquals("name", params.get("body").getFirst().get("name"));
            assertEquals(true, params.get("body").getFirst().get("required"));
            assertEquals("notify", params.get("query").getFirst().get("name"));
            assertEquals("api_key", params.get("header").getFirst().get("name"));
            assertEquals("file", params.get("form").getFirst().get("name"));
        }
    }

    private static void setEnvReader(Function<String, String> reader) throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    private static HttpServer createServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static void writePlain(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
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
            result.merge(key, value, (left, right) -> left + "," + right);
        }
        return result;
    }
}
