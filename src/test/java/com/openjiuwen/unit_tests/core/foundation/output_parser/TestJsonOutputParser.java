/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.output_parser;

import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JsonOutputParser.
 *
 * <p>Mirrors Python's tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py.</p>
 */
@DisplayName("TestJsonOutputParser")
class TestJsonOutputParser {

    private JsonOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonOutputParser();
    }

    @Nested
    @DisplayName("Parse single input tests")
    class ParseTests {

        @Test
        @DisplayName("Test parse valid JSON string")
        void testParseValidJsonString() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("{\"name\": \"test\", \"value\": 123}");

            assertNotNull(result);
            assertEquals("test", result.get("name"));
            assertEquals(123, result.get("value"));
        }

        @Test
        @DisplayName("Test parse valid JSON in markdown code block")
        void testParseValidJsonInMarkdown() {
            String markdownJson = "Here is some info:\n```json\n{\"item\": \"apple\", \"price\": 1.5}\n```\nThanks!";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(markdownJson);

            assertNotNull(result);
            assertEquals("apple", result.get("item"));
            assertEquals(1.5, result.get("price"));
        }

        @Test
        @DisplayName("Test parse valid JSON in AIMessage object")
        void testParseValidJsonInAIMessage() {
            AssistantMessage aiMessage = new AssistantMessage("```json\n{\"status\": \"success\", \"code\": 200}\n```");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(aiMessage);

            assertNotNull(result);
            assertEquals("success", result.get("status"));
            assertEquals(200, result.get("code"));
        }

        @Test
        @DisplayName("Test parse invalid JSON string")
        void testParseInvalidJsonString() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("{\"name\": \"test\", \"value\": 123,");
            assertNull(result);
        }

        @Test
        @DisplayName("Test parse non-JSON text")
        void testParseNonJsonString() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("This is just plain text.");
            assertNull(result);
        }

        @Test
        @DisplayName("Test parse empty string")
        void testParseEmptyString() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("");
            assertNull(result);
        }

        @Test
        @DisplayName("Test parse None input")
        void testParseNoneInput() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse((String) null);
            assertNull(result);
        }

        @Test
        @DisplayName("Test parse complex JSON structure")
        void testParseComplexJson() {
            String complexJson = "```json\n"
                    + "{\n"
                    + "  \"users\": [\n"
                    + "    {\"id\": 1, \"name\": \"Alice\", \"active\": true},\n"
                    + "    {\"id\": 2, \"name\": \"Bob\", \"active\": false}\n"
                    + "  ],\n"
                    + "  \"metadata\": {\"total\": 2, \"page\": 1}\n"
                    + "}\n"
                    + "```";

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(complexJson);

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users = (List<Map<String, Object>>) result.get("users");
            assertEquals(2, users.size());
            assertEquals("Alice", users.get(0).get("name"));
            assertEquals("Bob", users.get(1).get("name"));
        }

        @Test
        @DisplayName("Test parse JSON with Unicode characters")
        void testParseJsonWithUnicode() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("{\"message\":\"你好，世界\"}");
            assertNotNull(result);
            assertEquals("你好，世界", result.get("message"));
        }

        @Test
        @DisplayName("Test parse JSON with special characters")
        void testParseJsonWithSpecialCharacters() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("{\"path\":\"C:/temp/file.txt\",\"ok\":true}");
            assertNotNull(result);
            assertEquals("C:/temp/file.txt", result.get("path"));
            assertEquals(true, result.get("ok"));
        }

        @Test
        @DisplayName("Test parse JSON with escaped strings")
        void testParseJsonWithEscapedStrings() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("{\"quote\":\"He said \\\"hi\\\"\"}");
            assertNotNull(result);
            assertEquals("He said \"hi\"", result.get("quote"));
        }
    }

    @Nested
    @DisplayName("Stream parse tests")
    class StreamParseTests {

        @Test
        @DisplayName("Test stream parse valid JSON chunks")
        void testStreamParseValidJsonChunks() {
            List<String> chunks = List.of("```json\n", "{\"data\": ", "\"value\"}\n", "```");
            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(Map.of("data", "value"), parsedObjects.get(0));
        }

        @Test
        @DisplayName("Test stream parse fragmented JSON chunks")
        void testStreamParseFragmentedJsonChunks() {
            List<String> chunks = List.of(
                    "Some text before.\n",
                    "```json\n",
                    "{\"id\": 1,",
                    "\"name\": \"",
                    "Fragmented Item\"",
                    "}\n",
                    "```\n",
                    "More text after.");
            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(Map.of("id", 1, "name", "Fragmented Item"), parsedObjects.get(0));
        }

        @Test
        @DisplayName("Test stream parse multiple JSON objects")
        void testStreamParseMultipleJsonObjects() {
            List<String> chunks = List.of("```json\n{\"a\":1}\n```", "Some text.", "```json\n{\"b\":2}\n```");
            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(2, parsedObjects.size());
            assertEquals(List.of(Map.of("a", 1), Map.of("b", 2)), parsedObjects);
        }

        @Test
        @DisplayName("Test stream parse invalid JSON chunks")
        void testStreamParseInvalidJsonChunks() {
            List<String> chunks = List.of("```json\n", "{\"data\": ", "\"value\" \n", "```");
            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertTrue(parsedObjects.isEmpty());
        }

        @Test
        @DisplayName("Test stream parse empty chunks")
        void testStreamParseEmptyChunks() {
            List<String> chunks = new ArrayList<>();
            chunks.add("");
            chunks.add(null);
            chunks.add("");
            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertTrue(parsedObjects.isEmpty());
        }

        @Test
        @DisplayName("Test stream parse AssistantMessageChunk")
        void testStreamParseAssistantMessageChunks() {
            List<AssistantMessageChunk> chunks = List.of(
                    AssistantMessageChunk.builder().content("```json\n{\"status\":").build(),
                    AssistantMessageChunk.builder().content("\"ok\"}\n```").build());
            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(Map.of("status", "ok"), parsedObjects.get(0));
        }
    }
}
