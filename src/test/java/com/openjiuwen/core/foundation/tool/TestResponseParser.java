/* *  Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved. */
package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.service_api.parser.JsonResponseParser;
import com.openjiuwen.core.foundation.tool.service_api.parser.TextResponseParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
        @DisplayName("test missing content type falls back to Accept header")
        void testMissingContentTypeWithAcceptHeader() {
            JsonResponseParser parser = new JsonResponseParser();
            assertTrue(parser.canParse("", 200, Map.of("Accept", "application/json")));
            assertTrue(parser.canParse("", 200, Map.of("Accept", "application/ld+json")));
            assertTrue(parser.canParse(null, 200, Map.of("Accept", "application/json")));
            assertFalse(parser.canParse("", 200, Map.of("Accept", "text/html")));
            assertFalse(parser.canParse("", 200, null));
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
        @DisplayName("test parse RFC 6839 JSON bytes")
        void testParseRfc6839JsonBytes() {
            JsonResponseParser parser = new JsonResponseParser();
            byte[] jsonBytes = "{\"@context\":\"https://json-ld.org\",\"name\":\"test\"}"
                    .getBytes(StandardCharsets.UTF_8);

            Object result = parser.parse(jsonBytes, "application/ld+json");

            assertEquals(Map.of("@context", "https://json-ld.org", "name", "test"), result);
        }

        @Test
        @DisplayName("test parse HAL JSON bytes")
        void testParseHalJsonBytes() {
            JsonResponseParser parser = new JsonResponseParser();
            byte[] jsonBytes = ("{\"_links\":{\"self\":{\"href\":\"/api/users/123\"}},"
                    + "\"id\":123,\"name\":\"test_user\"}").getBytes(StandardCharsets.UTF_8);

            Object result = parser.parse(jsonBytes, "application/hal+json");

            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals(123, map.get("id"));
            assertEquals("test_user", map.get("name"));
            assertTrue(map.containsKey("_links"));
        }

        @Test
        @DisplayName("test parse empty JSON bytes")
        void testParseEmptyBytesJson() {
            JsonResponseParser parser = new JsonResponseParser();
            assertEquals(Map.of(), parser.parse(new byte[0], "application/json"));
        }

        @Test
        @DisplayName("test parse null JSON bytes")
        void testParseNoneBytesJson() {
            JsonResponseParser parser = new JsonResponseParser();
            assertEquals(Map.of(), parser.parse(null, "application/json"));
        }

        @Test
        @DisplayName("test parse video+json bytes")
        void testParseVideoPlusJson() {
            JsonResponseParser parser = new JsonResponseParser();
            byte[] jsonBytes = "{\"videoId\":\"abc123\",\"duration\":300,\"status\":\"ready\"}"
                    .getBytes(StandardCharsets.UTF_8);

            Object result = parser.parse(jsonBytes, "application/video+json");

            assertEquals(Map.of("videoId", "abc123", "duration", 300, "status", "ready"), result);
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
            
            assertInstanceOf(List.class, result);
            assertEquals(2, ((List<?>) result).size());
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
            assertTrue(parser.canParse("text/css", 200, null));
            assertTrue(parser.canParse("text/csv", 200, null));
        }

        @Test
        @DisplayName("test generic text content types are recognized")
        void testGenericTextTypes() {
            TextResponseParser parser = new TextResponseParser();
            assertTrue(parser.canParse("text/markdown", 200, null));
            assertTrue(parser.canParse("text/rtf", 200, null));
        }

        @Test
        @DisplayName("test XML content types are recognized")
        void testXmlContentTypes() {
            TextResponseParser parser = new TextResponseParser();
            assertTrue(parser.canParse("application/xml", 200, null));
            assertTrue(parser.canParse("application/xhtml+xml", 200, null));
            assertFalse(parser.canParse("application/json", 200, null));
        }

        @Test
        @DisplayName("test JavaScript content types are recognized")
        void testJavascriptContentTypes() {
            TextResponseParser parser = new TextResponseParser();
            assertTrue(parser.canParse("text/javascript", 200, null));
            assertTrue(parser.canParse("application/javascript", 200, null));
        }

        @Test
        @DisplayName("test non text content types are not recognized")
        void testNonTextContentTypes() {
            TextResponseParser parser = new TextResponseParser();
            assertFalse(parser.canParse("application/json", 200, null));
            assertFalse(parser.canParse("image/png", 200, null));
            assertFalse(parser.canParse("application/pdf", 200, null));
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

        @Test
        @DisplayName("test parse HTML bytes")
        void testParseHtmlBytes() {
            TextResponseParser parser = new TextResponseParser();
            byte[] textBytes = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>"
                    .getBytes(StandardCharsets.UTF_8);

            Object result = parser.parse(textBytes, "text/html");

            assertEquals("<!DOCTYPE html><html><body><h1>Hello</h1></body></html>", result);
        }

        @Test
        @DisplayName("test parse XML bytes")
        void testParseXmlBytes() {
            TextResponseParser parser = new TextResponseParser();
            byte[] textBytes = "<?xml version=\"1.0\"?><response><status>ok</status></response>"
                    .getBytes(StandardCharsets.UTF_8);

            Object result = parser.parse(textBytes, "application/xml");

            assertEquals("<?xml version=\"1.0\"?><response><status>ok</status></response>", result);
        }

        @Test
        @DisplayName("test parse empty text bytes")
        void testParseEmptyBytesText() {
            TextResponseParser parser = new TextResponseParser();
            assertEquals("", parser.parse(new byte[0], "text/plain"));
        }

        @Test
        @DisplayName("test parse null text bytes")
        void testParseNoneBytesText() {
            TextResponseParser parser = new TextResponseParser();
            assertEquals("", parser.parse(null, "text/plain"));
        }
    }
}
