/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for RestfulApi.
 * <p>
 * Mirrors Python's {@code test_restfulapi.py} from
 * {@code tests/unit_tests/core/foundation/tool/test_restfulapi.py}.
 */
@DisplayName("RestfulApi Tests")
class TestRestfulApi {

    @Nested
    @DisplayName("RestfulApi Structure Tests")
    class TestRestfulApiStructure {

        @Test
        @DisplayName("RestfulApi can be created")
        void testRestfulApiCanBeCreated() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("GET")
                    .build();

            RestfulApi api = new RestfulApi(card);

            assertNotNull(api);
            assertEquals("test", api.getCard().getName());
        }
    }

    @Nested
    @DisplayName("RestfulApiCard Tests")
    class TestRestfulApiCard {

        @Test
        @DisplayName("RestfulApiCard can be created")
        void testRestfulApiCardCanBeCreated() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("GET")
                    .build();

            assertNotNull(card);
            assertEquals("http://example.com", card.getUrl());
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
    @DisplayName("HTTP Methods Tests")
    class TestHttpMethods {

        @Test
        @DisplayName("all Python-supported HTTP methods are accepted")
        void testAllSupportedMethods() {
            Set<String> expected = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

            assertEquals(expected, RestfulApiCard.SUPPORTED_METHODS);
            for (String method : expected) {
                RestfulApiCard card = RestfulApiCard.builder()
                        .name("test_" + method.toLowerCase())
                        .description("test")
                        .url("http://example.com")
                        .method(method)
                        .build();
                assertDoesNotThrow(() -> new RestfulApi(card), method + " should be accepted");
            }
        }

        @Test
        @DisplayName("invalid HTTP method is rejected")
        void testInvalidMethodRejected() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("INVALID_METHOD")
                    .build();

            assertThrows(Throwable.class, () -> new RestfulApi(card));
        }
    }

    @Nested
    @DisplayName("URL Configuration Tests")
    class TestUrlConfiguration {

        @Test
        @DisplayName("url can be configured")
        void testUrlCanBeConfigured() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("https://example.com/api")
                    .method("GET")
                    .build();

            RestfulApi api = new RestfulApi(card);

            assertEquals("https://example.com/api", ((RestfulApiCard) api.getCard()).getUrl());
        }

        @Test
        @DisplayName("path params require path location schema")
        void testPathParamValidation() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of("type", "integer"))))
                    .build();

            assertThrows(Throwable.class, () -> new RestfulApi(card));
        }
    }

    @Nested
    @DisplayName("SSL Configuration Tests")
    class TestSslConfiguration {

        @Test
        @DisplayName("HTTPS url can be configured")
        void testSslCanBeConfigured() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("secure_api")
                    .description("secure test")
                    .url("https://example.com")
                    .method("GET")
                    .build();

            RestfulApi api = new RestfulApi(card);

            assertTrue(((RestfulApiCard) api.getCard()).getUrl().startsWith("https://"));
        }
    }
}
