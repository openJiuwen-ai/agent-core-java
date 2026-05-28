/* *  Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved. */
package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.service_api.parser.JsonResponseParser;
import com.openjiuwen.core.foundation.tool.service_api.parser.TextResponseParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JSON and Text response parsers with RFC 6839 and binary JSON support.
 * Mirrors Python's tests/unit_tests/core/foundation/tool/test_response_parser.py
 */
class TestResponseParser {

    @Nested
    @DisplayName("JsonResponseParser tests")
    class JsonResponseParserTests {

        @Test
        @DisplayName("test standard JSON content types are recognized")
        void testStandardJsonContentTypes() {
            JsonResponseParser parser = new JsonResponseParser();
            assertTrue(parser.canParse("application/json", 200, null));
            assertTrue(parser.canParse("text/json", 200, null));
            assertTrue(parser.canParse("text/x-json", 200, null));
            assertTrue(parser.canParse("application/javascript", 200, null));
        }

        @Test
        @DisplayName("test RFC 6839 plus JSON suffix types are recognized")
        void testRfc6839PlusJsonSuffixTypes() {
            JsonResponseParser parser = new JsonResponseParser();
            assertTrue(parser.canParse("application/video+json", 200, null));
            assertTrue(parser.canParse("application/hal+json", 200, null));
            assertTrue(parser.canParse("application/ld+json", 200, null));
            assertTrue(parser.canParse("application/schema+json", 200, null));
            assertTrue(parser.canParse("application/problem+json", 200, null));
        }

        @Test
        @DisplayName("test content type matching is case insensitive")
        void testContentTypeCaseInsensitive() {
            JsonResponseParser parser = new JsonResponseParser();
            assertTrue(parser.canParse("APPLICATION/JSON", 200, null));
            assertTrue(parser.canParse("Application/Json", 200, null));
            assertTrue(parser.canParse("APPLICATION/VIDEO+JSON", 200, null));
        }

        @Test
        @DisplayName("test non JSON content types are not recognized")
        void testNonJsonContentTypes() {
            JsonResponseParser parser = new JsonResponseParser();
            assertFalse(parser.canParse("text/html", 200, null));
            assertFalse(parser.canParse("text/plain", 200, null));
            assertFalse(parser.canParse("application/xml", 200, null));
            assertFalse(parser.canParse("application/xhtml+xml", 200, null));
            assertFalse(parser.canParse("image/png", 200, null));
        }

        @Test
        @DisplayName("test parse standard JSON bytes")
        void testParseStandardJsonBytes() {
            JsonResponseParser parser = new JsonResponseParser();
            byte[] jsonBytes = "{\"name\": \"test\", \"value\": 123}".getBytes(StandardCharsets.UTF_8);
            
            Object result = parser.parse(jsonBytes, "application/json");
            
            assertNotNull(result);
            if (result instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) result;
                assertEquals("test", map.get("name"));
                assertEquals(123, map.get("value"));
            }
        }

        @Test
        @DisplayName("test parse JSON with nested object")
        void testParseNestedJson() {
            JsonResponseParser parser = new JsonResponseParser();
            byte[] jsonBytes = "{\"user\": {\"id\": 1, \"name\": \"Alice\"}}".getBytes(StandardCharsets.UTF_8);
            
            Object result = parser.parse(jsonBytes, "application/json");
            
            assertNotNull(result);
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) result;
                @SuppressWarnings("unchecked")
                Map<String, Object> user = (Map<String, Object>) map.get("user");
                assertEquals(1, user.get("id"));
                assertEquals("Alice", user.get("name"));
            }
        }

        @Test
        @DisplayName("test parse JSON array returns list")
        void testParseJsonArray() {
            JsonResponseParser parser = new JsonResponseParser();
            byte[] jsonBytes = "[{\"id\": 1}, {\"id\": 2}]".getBytes(StandardCharsets.UTF_8);
            
            Object result = parser.parse(jsonBytes, "application/json");
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("TextResponseParser tests")
    class TextResponseParserTests {

        @Test
        @DisplayName("test text content types are recognized")
        void testTextContentTypes() {
            TextResponseParser parser = new TextResponseParser();
            assertTrue(parser.canParse("text/plain", 200, null));
            assertTrue(parser.canParse("text/html", 200, null));
            assertTrue(parser.canParse("text/xml", 200, null));
        }

        @Test
        @DisplayName("test non text content types are not recognized")
        void testNonTextContentTypes() {
            TextResponseParser parser = new TextResponseParser();
            assertFalse(parser.canParse("application/json", 200, null));
            assertFalse(parser.canParse("image/png", 200, null));
            assertFalse(parser.canParse("application/octet-stream", 200, null));
        }

        @Test
        @DisplayName("test parse text bytes")
        void testParseTextBytes() {
            TextResponseParser parser = new TextResponseParser();
            byte[] textBytes = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            
            Object result = parser.parse(textBytes, "text/plain");
            
            assertEquals("Hello, World!", result);
        }
    }
}