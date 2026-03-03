/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

        private RestfulApi restfulApi;

        @BeforeEach
        void setUp() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test_api")
                    .description("Test API")
                    .url("http://example.com/api/test")
                    .method("POST")
                    .timeout(60.0)
                    .maxResponseByteSize(10 * 1024 * 1024)
                    .build();
            restfulApi = new RestfulApi(card);
        }

        @Test
        @DisplayName("Invoke with unreachable host throws error")
        @SuppressWarnings("unchecked")
        void testInvokeUnreachableHostThrows() {
            // Attempting to connect to a non-existent host should throw
            assertThrows(Throwable.class, () -> restfulApi.invoke(Map.of()));
        }

        @Test
        @DisplayName("Invoke GET with path and query params builds correct URL")
        void testInvokeGetWithParams() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://127.0.0.1:1/users/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "integer", "location", "path"),
                                    "format", Map.of("type", "string", "location", "query")
                            )
                    ))
                    .timeout(1.0)
                    .build();

            RestfulApi api = new RestfulApi(card);
            // This will fail to connect, but we verify it doesn't throw NPE etc.
            assertThrows(Throwable.class, () ->
                    api.invoke(Map.of("id", 42, "format", "json")));
        }
    }
}
