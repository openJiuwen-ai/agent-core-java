/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_response_parser} in
 * {@code tests/unit_tests/core/foundation/tool/test_response_parser.py}.
 */
class ResponseParserTest {

    @Test
    void standardJsonContentTypes() {
        JsonResponseParser parser = new JsonResponseParser();
        assertTrue(parser.canParse("application/json", 200, Map.of()));
        assertTrue(parser.canParse("text/json", 200, Map.of()));
        assertTrue(parser.canParse("text/x-json", 200, Map.of()));
        assertTrue(parser.canParse("application/javascript", 200, Map.of()));
    }

    @Test
    void rfc6839PlusJsonSuffixTypes() {
        JsonResponseParser parser = new JsonResponseParser();
        assertTrue(parser.canParse("application/video+json", 200, Map.of()));
        assertTrue(parser.canParse("application/hal+json", 200, Map.of()));
        assertTrue(parser.canParse("application/ld+json", 200, Map.of()));
        assertTrue(parser.canParse("application/schema+json", 200, Map.of()));
        assertTrue(parser.canParse("application/problem+json", 200, Map.of()));
    }

    @Test
    void contentTypeCaseInsensitive() {
        JsonResponseParser parser = new JsonResponseParser();
        assertTrue(parser.canParse("APPLICATION/JSON", 200, Map.of()));
        assertTrue(parser.canParse("Application/Json", 200, Map.of()));
        assertTrue(parser.canParse("APPLICATION/VIDEO+JSON", 200, Map.of()));
    }

    @Test
    void missingContentTypeWithAcceptHeader() {
        JsonResponseParser parser = new JsonResponseParser();
        assertTrue(parser.canParse("", 200, Map.of("Accept", "application/json")));
        assertTrue(parser.canParse("", 200, Map.of("Accept", "application/ld+json")));
        assertTrue(parser.canParse(null, 200, Map.of("Accept", "application/json")));
        assertFalse(parser.canParse("", 200, Map.of("Accept", "text/html")));
        assertFalse(parser.canParse("", 200, Map.of()));
    }

    @Test
    void nonJsonContentTypes() {
        JsonResponseParser parser = new JsonResponseParser();
        assertFalse(parser.canParse("text/html", 200, Map.of()));
        assertFalse(parser.canParse("text/plain", 200, Map.of()));
        assertFalse(parser.canParse("application/xml", 200, Map.of()));
        assertFalse(parser.canParse("application/xhtml+xml", 200, Map.of()));
        assertFalse(parser.canParse("image/png", 200, Map.of()));
    }

    @Test
    void parseStandardJsonBytes() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse(
                "{\"name\": \"test\", \"value\": 123}".getBytes(),
                "utf-8",
                Map.of("Content-Type", "application/json")
        );
        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("test", map.get("name"));
        assertEquals(123, ((Number) map.get("value")).intValue());
    }

    @Test
    void parseRfc6839JsonBytes() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse(
                "{\"@context\": \"https://json-ld.org\", \"name\": \"test\"}".getBytes(),
                "utf-8",
                Map.of("Content-Type", "application/ld+json")
        );
        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("https://json-ld.org", map.get("@context"));
        assertEquals("test", map.get("name"));
    }

    @Test
    void parseHalJsonBytes() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse(
                "{\"_links\": {\"self\": {\"href\": \"/api/users/123\"}}, \"id\": 123, \"name\": \"test_user\"}"
                        .getBytes(),
                "utf-8",
                Map.of("Content-Type", "application/hal+json")
        );
        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(123, ((Number) map.get("id")).intValue());
        assertEquals("test_user", map.get("name"));
        assertTrue(map.containsKey("_links"));
    }

    @Test
    void parseEmptyBytesJson() {
        JsonResponseParser parser = new JsonResponseParser();
        assertEquals(Map.of(), parser.parse(new byte[0], null, Map.of("Content-Type", "application/json")));
    }

    @Test
    void parseNoneBytesJson() {
        JsonResponseParser parser = new JsonResponseParser();
        assertEquals(Map.of(), parser.parse(null, null, Map.of("Content-Type", "application/json")));
    }

    @Test
    void parseVideoPlusJson() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse(
                "{\"videoId\": \"abc123\", \"duration\": 300, \"status\": \"ready\"}".getBytes(),
                "utf-8",
                Map.of("Content-Type", "application/video+json")
        );
        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("abc123", map.get("videoId"));
        assertEquals(300, ((Number) map.get("duration")).intValue());
        assertEquals("ready", map.get("status"));
    }

    @Test
    void standardTextContentTypes() {
        TextResponseParser parser = new TextResponseParser();
        assertTrue(parser.canParse("text/plain", 200, Map.of()));
        assertTrue(parser.canParse("text/html", 200, Map.of()));
        assertTrue(parser.canParse("text/xml", 200, Map.of()));
        assertTrue(parser.canParse("text/css", 200, Map.of()));
        assertTrue(parser.canParse("text/csv", 200, Map.of()));
    }

    @Test
    void genericTextTypes() {
        TextResponseParser parser = new TextResponseParser();
        assertTrue(parser.canParse("text/markdown", 200, Map.of()));
        assertTrue(parser.canParse("text/rtf", 200, Map.of()));
    }

    @Test
    void xmlContentTypes() {
        TextResponseParser parser = new TextResponseParser();
        assertTrue(parser.canParse("application/xml", 200, Map.of()));
        assertTrue(parser.canParse("application/xhtml+xml", 200, Map.of()));
        assertFalse(parser.canParse("application/json", 200, Map.of()));
    }

    @Test
    void javascriptContentTypes() {
        TextResponseParser parser = new TextResponseParser();
        assertTrue(parser.canParse("text/javascript", 200, Map.of()));
        assertTrue(parser.canParse("application/javascript", 200, Map.of()));
    }

    @Test
    void nonTextContentTypes() {
        TextResponseParser parser = new TextResponseParser();
        assertFalse(parser.canParse("image/png", 200, Map.of()));
        assertFalse(parser.canParse("application/pdf", 200, Map.of()));
        assertFalse(parser.canParse("application/octet-stream", 200, Map.of()));
    }

    @Test
    void parsePlainTextBytes() {
        TextResponseParser parser = new TextResponseParser();
        assertEquals("Hello, World!", parser.parse(
                "Hello, World!".getBytes(),
                null,
                Map.of("Content-Type", "text/plain")
        ));
    }

    @Test
    void parseHtmlBytes() {
        TextResponseParser parser = new TextResponseParser();
        assertEquals(
                "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>",
                parser.parse(
                        "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>".getBytes(),
                        null,
                        Map.of("Content-Type", "text/html")
                )
        );
    }

    @Test
    void parseXmlBytes() {
        TextResponseParser parser = new TextResponseParser();
        assertEquals(
                "<?xml version=\"1.0\"?><response><status>ok</status></response>",
                parser.parse(
                        "<?xml version=\"1.0\"?><response><status>ok</status></response>".getBytes(),
                        null,
                        Map.of("Content-Type", "application/xml")
                )
        );
    }

    @Test
    void parseEmptyBytesText() {
        TextResponseParser parser = new TextResponseParser();
        assertEquals("", parser.parse(new byte[0], null, Map.of("Content-Type", "text/plain")));
    }

    @Test
    void parseNoneBytesText() {
        TextResponseParser parser = new TextResponseParser();
        assertEquals("", parser.parse(null, null, Map.of("Content-Type", "text/plain")));
    }

    @Test
    void parserRegistryUsesCurrentPythonSelectionFlow() {
        Object result = ParserRegistry.getInstance().parse(
                Map.of("Content-Type", "application/json"),
                "{\"ok\": true}".getBytes(),
                200
        );
        assertInstanceOf(Map.class, result);
    }

    @Test
    void parserRegistryThrowsWhenNoParserMatches() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ParserRegistry.getInstance().parse(
                        Map.of("Content-Type", "image/png"),
                        new byte[]{1, 2, 3},
                        200
                )
        );
    }
}
