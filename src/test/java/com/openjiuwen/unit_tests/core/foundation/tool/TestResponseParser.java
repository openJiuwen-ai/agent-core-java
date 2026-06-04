/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.service_api.parser.JsonResponseParser;
import com.openjiuwen.core.foundation.tool.service_api.parser.TextResponseParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for JSON and text response parsers.
 *
 * <p>Mirrors Python's
 * {@code tests.unit_tests.core.foundation.tool.test_response_parser}.</p>
 */
class TestResponseParser {

    @Test
    void testStandardJsonContentTypes() {
        JsonResponseParser parser = new JsonResponseParser();

        assertTrue(parser.canParse("application/json", 200, null));
        assertTrue(parser.canParse("text/json", 200, null));
        assertTrue(parser.canParse("text/x-json", 200, null));
        assertTrue(parser.canParse("application/javascript", 200, null));
    }

    @Test
    void testRfc6839PlusJsonSuffixTypes() {
        JsonResponseParser parser = new JsonResponseParser();

        assertTrue(parser.canParse("application/video+json", 200, null));
        assertTrue(parser.canParse("application/hal+json", 200, null));
        assertTrue(parser.canParse("application/ld+json", 200, null));
        assertTrue(parser.canParse("application/schema+json", 200, null));
        assertTrue(parser.canParse("application/problem+json", 200, null));
    }

    @Test
    void testContentTypeCaseInsensitive() {
        JsonResponseParser parser = new JsonResponseParser();

        assertTrue(parser.canParse("APPLICATION/JSON", 200, null));
        assertTrue(parser.canParse("Application/Json", 200, null));
        assertTrue(parser.canParse("APPLICATION/VIDEO+JSON", 200, null));
    }

    @Test
    void testMissingContentTypeWithAcceptHeader() {
        JsonResponseParser parser = new JsonResponseParser();

        assertTrue(parser.canParse("", 200, Map.of("Accept", "application/json")));
        assertTrue(parser.canParse("", 200, Map.of("Accept", "application/ld+json")));
        assertTrue(parser.canParse(null, 200, Map.of("Accept", "application/json")));
        assertFalse(parser.canParse("", 200, Map.of("Accept", "text/html")));
        assertFalse(parser.canParse("", 200, null));
    }

    @Test
    void testNonJsonContentTypes() {
        JsonResponseParser parser = new JsonResponseParser();

        assertFalse(parser.canParse("text/html", 200, null));
        assertFalse(parser.canParse("text/plain", 200, null));
        assertFalse(parser.canParse("application/xml", 200, null));
        assertFalse(parser.canParse("application/xhtml+xml", 200, null));
        assertFalse(parser.canParse("image/png", 200, null));
    }

    @Test
    void testParseStandardJsonBytes() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse("{\"name\": \"test\", \"value\": 123}".getBytes(StandardCharsets.UTF_8),
                "application/json");

        assertEquals(Map.of("name", "test", "value", 123), result);
    }

    @Test
    void testParseRfc6839JsonBytes() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse("{\"@context\": \"https://json-ld.org\", \"name\": \"test\"}"
                .getBytes(StandardCharsets.UTF_8), "application/ld+json");

        assertEquals(Map.of("@context", "https://json-ld.org", "name", "test"), result);
    }

    @Test
    void testParseHalJsonBytes() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse(
                "{\"_links\": {\"self\": {\"href\": \"/api/users/123\"}}, \"id\": 123, \"name\": \"test_user\"}"
                        .getBytes(StandardCharsets.UTF_8),
                "application/hal+json");
        Map<?, ?> map = (Map<?, ?>) result;

        assertEquals(123, map.get("id"));
        assertEquals("test_user", map.get("name"));
        assertTrue(map.containsKey("_links"));
    }

    @Test
    void testParseEmptyBytesJson() {
        JsonResponseParser parser = new JsonResponseParser();

        assertEquals(Map.of(), parser.parse(new byte[0], "application/json"));
    }

    @Test
    void testParseNoneBytesJson() {
        JsonResponseParser parser = new JsonResponseParser();

        assertEquals(Map.of(), parser.parse(null, "application/json"));
    }

    @Test
    void testParseVideoPlusJson() {
        JsonResponseParser parser = new JsonResponseParser();
        Object result = parser.parse("{\"videoId\": \"abc123\", \"duration\": 300, \"status\": \"ready\"}"
                .getBytes(StandardCharsets.UTF_8), "application/video+json");

        assertEquals(Map.of("videoId", "abc123", "duration", 300, "status", "ready"), result);
    }

    @Test
    void testStandardTextContentTypes() {
        TextResponseParser parser = new TextResponseParser();

        assertTrue(parser.canParse("text/plain", 200, null));
        assertTrue(parser.canParse("text/html", 200, null));
        assertTrue(parser.canParse("text/xml", 200, null));
        assertTrue(parser.canParse("text/css", 200, null));
        assertTrue(parser.canParse("text/csv", 200, null));
    }

    @Test
    void testGenericTextTypes() {
        TextResponseParser parser = new TextResponseParser();

        assertTrue(parser.canParse("text/markdown", 200, null));
        assertTrue(parser.canParse("text/rtf", 200, null));
    }

    @Test
    void testXmlContentTypes() {
        TextResponseParser parser = new TextResponseParser();

        assertTrue(parser.canParse("application/xml", 200, null));
        assertTrue(parser.canParse("application/xhtml+xml", 200, null));
        assertFalse(parser.canParse("application/json", 200, null));
    }

    @Test
    void testJavascriptContentTypes() {
        TextResponseParser parser = new TextResponseParser();

        assertTrue(parser.canParse("text/javascript", 200, null));
        assertTrue(parser.canParse("application/javascript", 200, null));
    }

    @Test
    void testNonTextContentTypes() {
        TextResponseParser parser = new TextResponseParser();

        assertFalse(parser.canParse("image/png", 200, null));
        assertFalse(parser.canParse("application/pdf", 200, null));
        assertFalse(parser.canParse("application/octet-stream", 200, null));
    }

    @Test
    void testParsePlainTextBytes() {
        TextResponseParser parser = new TextResponseParser();

        assertEquals("Hello, World!", parser.parse("Hello, World!".getBytes(StandardCharsets.UTF_8), "text/plain"));
    }

    @Test
    void testParseHtmlBytes() {
        TextResponseParser parser = new TextResponseParser();
        String html = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>";

        assertEquals(html, parser.parse(html.getBytes(StandardCharsets.UTF_8), "text/html"));
    }

    @Test
    void testParseXmlBytes() {
        TextResponseParser parser = new TextResponseParser();
        String xml = "<?xml version=\"1.0\"?><response><status>ok</status></response>";

        assertEquals(xml, parser.parse(xml.getBytes(StandardCharsets.UTF_8), "application/xml"));
    }

    @Test
    void testParseEmptyBytesText() {
        TextResponseParser parser = new TextResponseParser();

        assertEquals("", parser.parse(new byte[0], "text/plain"));
    }

    @Test
    void testParseNoneBytesText() {
        TextResponseParser parser = new TextResponseParser();

        assertEquals("", parser.parse(null, "text/plain"));
    }
}
