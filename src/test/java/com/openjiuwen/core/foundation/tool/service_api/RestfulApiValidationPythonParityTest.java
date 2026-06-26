/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.security.UrlUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_validation} in
 * {@code tests/unit_tests/core/foundation/tool/test_validation.py}.
 */
class RestfulApiValidationPythonParityTest {

    @BeforeAll
    static void disableSsrfProtectionForLocalParityServers() throws Exception {
        setUrlUtilsEnvReader(key -> "SSRF_PROTECT_ENABLED".equals(key) ? "false" : System.getenv(key));
    }

    @AfterAll
    static void resetUrlUtilsEnvReader() throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        method.setAccessible(true);
        method.invoke(null);
    }

    @Test
    void testRestfulApiCardValidation() {
        RestfulApiCard card = RestfulApiCard.builder()
                .name("test_api")
                .url("http://203.0.113.1/users")
                .method("GET")
                .headers(Map.of("Content-Type", "application/json"))
                .queries(Map.of("page", 1, "limit", 10))
                .timeout(30.0d)
                .maxResponseByteSize(10 * 1024 * 1024)
                .build();

        assertEquals("GET", card.getMethod());
        assertEquals("http://203.0.113.1/users", card.getUrl());

        assertThrows(BaseError.class, () -> RestfulApiCard.builder()
                .name("test_api")
                .url("http://203.0.113.1/users")
                .method("INVALID_METHOD")
                .headers(Map.of("Content-Type", "application/json"))
                .build());

        RestfulApiCard pathCard = RestfulApiCard.builder()
                .name("test_api")
                .url("http://203.0.113.1/users/{id}")
                .method("GET")
                .headers(Map.of("Content-Type", "application/json"))
                .inputParams(pathSchema("id"))
                .build();
        assertEquals("http://203.0.113.1/users/{id}", pathCard.getUrl());

        assertThrows(BaseError.class, () -> RestfulApiCard.builder()
                .name("test_api")
                .url("not-a-url")
                .method("GET")
                .headers(Map.of("Content-Type", "application/json"))
                .build());
    }

    @Test
    void testRestfulApiAuthValidation() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            writeJson(exchange, 200, "{\"id\":1,\"name\":\"test\"}");
        });

        try {
            RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                    .name("test_api")
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/users/{id}")
                    .method("GET")
                    .headers(Map.of("Content-Type", "application/json"))
                    .inputParams(pathSchema("id"))
                    .timeout(5.0d)
                    .build());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of("id", 1));

            assertNotNull(result);
            assertEquals(200, result.get("code"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals(1, data.get("id"));
            assertEquals("/users/1", requestPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
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
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/users/{id}/posts/{post_id}")
                    .method("GET")
                    .headers(Map.of("Content-Type", "application/json"))
                    .queries(Map.of("default_param", "default_value"))
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "integer", "description", "User ID", "location", "path"),
                                    "post_id", Map.of("type", "integer", "description", "Post ID",
                                            "location", "path"),
                                    "page", Map.of("type", "integer", "description", "Page number",
                                            "location", "query"),
                                    "limit", Map.of("type", "integer", "description", "Items per page",
                                            "location", "query"),
                                    "custom_header", Map.of("type", "string", "description", "Custom header",
                                            "location", "header")),
                            "required", List.of("id", "post_id")))
                    .timeout(5.0d)
                    .build());

            Object result = api.invoke(Map.of(
                    "id", 1,
                    "post_id", 10,
                    "page", 1,
                    "limit", 10,
                    "custom_header", "test_header_value"
            ));

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

    private static Map<String, Object> pathSchema(String name) {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        name, Map.of("type", "integer", "description", "User ID", "location", "path")
                ),
                "required", List.of(name)
        );
    }

    private static void setUrlUtilsEnvReader(Function<String, String> reader) throws Exception {
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
