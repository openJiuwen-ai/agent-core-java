/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.foundation.tool.service_api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RestfulApi and RestfulApiCard.
 * Ported from Python: tests/unit_tests/core/foundation/tool/test_restfulapi.py
 */
class RestfulApiTest {

    @Nested
    @DisplayName("RestfulApiCard tests")
    class RestfulApiCardTests {

        @Test
        @DisplayName("toolInfo returns correct ToolInfo object")
        void testGetToolInfo() {
            Map<String, Object> inputParams = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "test", Map.of("description", "test", "type", "string", "default", "123")
                    ),
                    "required", new String[]{"test"}
            );

            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .inputParams(inputParams)
                    .url("http://127.0.0.1:8000")
                    .method("GET")
                    .build();

            ToolInfo result = (ToolInfo) card.toolInfo();
            assertEquals("test", result.getName());
            assertEquals("test", result.getDescription());
            assertNotNull(result.getParameters());
            assertEquals("object", result.getParameters().get("type"));
        }

        @Test
        @DisplayName("Card default method is POST")
        void testDefaultMethod() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .build();
            assertEquals("POST", card.getMethod());
        }

        @Test
        @DisplayName("Card default timeout is 60.0")
        void testDefaultTimeout() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .build();
            assertEquals(60.0, card.getTimeout());
        }

        @Test
        @DisplayName("Card default maxResponseByteSize is 10MB")
        void testDefaultMaxResponseByteSize() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .build();
            assertEquals(10 * 1024 * 1024, card.getMaxResponseByteSize());
        }

        @Test
        @DisplayName("Card custom headers/queries/paths")
        void testCustomHeadersQueriesPaths() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .headers(Map.of("Authorization", "Bearer token"))
                    .queries(Map.of("page", 1))
                    .paths(Map.of("version", "v1"))
                    .build();

            assertEquals(Map.of("Authorization", "Bearer token"), card.getHeaders());
            assertEquals(Map.of("page", 1), card.getQueries());
            assertEquals(Map.of("version", "v1"), card.getPaths());
        }
    }

    @Nested
    @DisplayName("RestfulApi construction tests")
    class RestfulApiConstructionTests {

        @Test
        @DisplayName("RestfulApi created with valid card")
        void testValidConstruction() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test_api")
                    .description("Test API")
                    .url("http://example.com/api/test")
                    .method("POST")
                    .build();

            RestfulApi api = new RestfulApi(card);
            assertNotNull(api);
            assertEquals("test_api", api.getCard().getName());
        }

        @Test
        @DisplayName("RestfulApi stream throws not supported error")
        void testStreamNotSupported() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test_api")
                    .description("Test API")
                    .url("http://example.com/api/test")
                    .method("GET")
                    .build();

            RestfulApi api = new RestfulApi(card);
            assertThrows(Throwable.class, () -> api.stream(Map.of()));
        }
    }

    @Nested
    @DisplayName("RestfulApi invoke tests")
    class RestfulApiInvokeTests {

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
        @DisplayName("Invoke with unreachable host throws error")
        void testInvokeUnreachableHostThrows() {
            RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                    .name("test_api")
                    .description("Test API")
                    .url("http://127.0.0.1:1/api/test")
                    .method("POST")
                    .timeout(1.0)
                    .build());
            assertThrows(Throwable.class, () -> api.invoke(Map.of()));
        }

        @Test
        @DisplayName("Invoke GET with path and query params builds correct URL")
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
                                        "keyword", Map.of("type", "string")
                                )
                        ))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(
                        Map.of("id", 42, "format", "json", "keyword", "hello"));

                assertEquals("/users/42", requestPath.get());
                assertEquals(Map.of("limit", "10", "format", "json", "keyword", "hello"), requestQuery.get());
                assertEquals("demo", requestHeader.get());
                assertEquals(200, result.get("code"));
                assertEquals("success", result.get("message"));
                assertEquals("OK", result.get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("Invoke with raise_for_status false returns error response")
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
                Map<String, Object> result = (Map<String, Object>) api.invoke(
                        Map.of(),
                        Map.of("raise_for_status", false));

                assertEquals(404, result.get("code"));
                assertEquals("Not Found", result.get("message"));
                assertEquals("Not Found", result.get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("Invoke PUT sends JSON body and replaces path parameters")
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

    @Nested
    @DisplayName("RestfulApi path parameter validation tests")
    class RestfulApiPathParameterValidationTests {

        @Test
        @DisplayName("URL with path param but no inputParams raises error")
        void testUrlWithPathParamButNoSchemaRaisesError() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("GET")
                    .build();

            Throwable error = assertThrows(Throwable.class, () -> new RestfulApi(card));
            assertTrue(error.getMessage().toLowerCase().contains("path"));
            assertTrue(error.getMessage().toLowerCase().contains("input_params")
                    || error.getMessage().toLowerCase().contains("input"));
        }

        @Test
        @DisplayName("URL path param must be marked location=path")
        void testUrlWithPathParamButNotMarkedInSchemaRaisesError() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of("type", "integer"))))
                    .build();

            Throwable error = assertThrows(Throwable.class, () -> new RestfulApi(card));
            assertTrue(error.getMessage().toLowerCase().contains("location")
                    || error.getMessage().toLowerCase().contains("path"));
        }

        @Test
        @DisplayName("URL with multiple path params requires all definitions")
        void testUrlWithMultiplePathParamsAllMustBeDefined() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/{version}/users/{userId}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("version", Map.of("type", "string", "location", "path"))))
                    .build();

            Throwable error = assertThrows(Throwable.class, () -> new RestfulApi(card));
            assertTrue(error.getMessage().contains("userId") || error.getMessage().toLowerCase().contains("path"));
        }

        @Test
        @DisplayName("URL with correct path param schema succeeds")
        void testUrlWithCorrectPathParamSchemaSucceeds() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of(
                                    "type", "integer",
                                    "description", "Activity ID",
                                    "location", "path")),
                            "required", List.of("id")))
                    .build();

            assertDoesNotThrow(() -> new RestfulApi(card));
        }

        @Test
        @DisplayName("getParametersByLocation groups GUI metadata")
        void testGetParametersByLocationHelper() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("update_activity")
                    .description("update")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("PUT")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "integer", "description", "Activity ID",
                                            "location", "path"),
                                    "name", Map.of("type", "string", "description", "Activity name",
                                            "location", "body"),
                                    "notify", Map.of("type", "boolean", "description", "Send notification",
                                            "location", "query"),
                                    "api_key", Map.of("type", "string", "description", "API Key",
                                            "location", "header")),
                            "required", List.of("id", "name")))
                    .build();

            Map<String, List<Map<String, Object>>> params = RestfulApi.getParametersByLocation(card);

            assertEquals(1, params.get("path").size());
            assertEquals("id", params.get("path").get(0).get("name"));
            assertEquals("integer", params.get("path").get(0).get("type"));
            assertEquals(true, params.get("path").get(0).get("required"));
            assertEquals("name", params.get("body").get(0).get("name"));
            assertEquals(true, params.get("body").get(0).get("required"));
            assertEquals("notify", params.get("query").get(0).get("name"));
            assertEquals(false, params.get("query").get(0).get("required"));
            assertEquals("api_key", params.get("header").get(0).get("name"));
        }
    }
}
