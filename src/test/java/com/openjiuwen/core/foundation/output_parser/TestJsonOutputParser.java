/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.foundation.output_parser;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonOutputParser.
 * Mirrors Python's tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py
 */
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
        @DisplayName("test parse valid JSON string")
        void testParseValidJsonString() {
            String jsonStr = "{\"name\": \"test\", \"value\": 123}";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(jsonStr);
            
            assertNotNull(result);
            assertEquals("test", result.get("name"));
            assertEquals(123, result.get("value"));
        }

        @Test
        @DisplayName("test parse valid JSON in markdown code block")
        void testParseValidJsonInMarkdown() {
            String markdownJson = "Here is some info:\n```json\n{\"item\": \"apple\", \"price\": 1.5}\n```\nThanks!";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(markdownJson);
            
            assertNotNull(result);
            assertEquals("apple", result.get("item"));
            assertEquals(1.5, result.get("price"));
        }

        @Test
        @DisplayName("test parse valid JSON in AIMessage")
        void testParseValidJsonInAiMessage() {
            AssistantMessage aiMessage = new AssistantMessage("```json\n{\"status\": \"success\", \"code\": 200}\n```");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(aiMessage);
            
            assertNotNull(result);
            assertEquals("success", result.get("status"));
            assertEquals(200, result.get("code"));
        }

        @Test
        @DisplayName("test parse invalid JSON string returns null")
        void testParseInvalidJsonString() {
            String invalidJson = "{\"name\": \"test\", \"value\": 123,";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(invalidJson);
            
            assertNull(result);
        }

        @Test
        @DisplayName("test parse non JSON string returns null")
        void testParseNonJsonString() {
            String nonJson = "This is just plain text.";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(nonJson);
            
            assertNull(result);
        }

        @Test
        @DisplayName("test parse empty string returns null")
        void testParseEmptyString() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse("");
            assertNull(result);
        }

        @Test
        @DisplayName("test parse null input returns null")
        void testParseNullInput() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse((String) null);
            assertNull(result);
        }

        @Test
        @DisplayName("test parse complex JSON structure")
        void testParseComplexJson() {
            String complexJson = "```json\n" +
                    "{\n" +
                    "    \"users\": [\n" +
                    "        {\"id\": 1, \"name\": \"Alice\", \"active\": true},\n" +
                    "        {\"id\": 2, \"name\": \"Bob\", \"active\": false}\n" +
                    "    ],\n" +
                    "    \"metadata\": {\n" +
                    "        \"total\": 2,\n" +
                    "        \"page\": 1\n" +
                    "    }\n" +
                    "}\n" +
                    "```";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parser.parse(complexJson);
            
            assertNotNull(result);
            assertNotNull(result.get("users"));
            assertNotNull(result.get("metadata"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users = (List<Map<String, Object>>) result.get("users");
            assertEquals(2, users.size());
            assertEquals("Alice", users.get(0).get("name"));
            assertEquals("Bob", users.get(1).get("name"));
        }
    }

    @Nested
    @DisplayName("Stream parse tests")
    class StreamParseTests {

        @Test
        @DisplayName("test stream parse valid JSON chunks")
        @SuppressWarnings("unchecked")
        void testStreamParseValidJsonChunks() {
            List<String> chunks = List.of(
                    "```json\n",
                    "{\"data\": ",
                    "\"value\"}\n",
                    "```"
            );
            Map<String, Object> expectedResult = Map.of("data", "value");

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(expectedResult, parsedObjects.get(0));
        }

        @Test
        @DisplayName("test stream parse fragmented JSON chunks")
        @SuppressWarnings("unchecked")
        void testStreamParseFragmentedJsonChunks() {
            List<String> chunks = List.of(
                    "Some text before.\n",
                    "```json\n",
                    "{\"id\": 1,",
                    "\"name\": \"",
                    "Fragmented Item\"",
                    "}\n",
                    "```\n",
                    "More text after."
            );
            Map<String, Object> expectedResult = Map.of("id", 1, "name", "Fragmented Item");

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(expectedResult, parsedObjects.get(0));
        }

        @Test
        @DisplayName("test stream parse multiple JSON objects")
        @SuppressWarnings("unchecked")
        void testStreamParseMultipleJsonObjects() {
            List<String> chunks = List.of(
                    "```json\n{\"a\":1}\n```",
                    "Some text.",
                    "```json\n{\"b\":2}\n```"
            );
            List<Object> expectedResults = List.of(
                    Map.of("a", 1),
                    Map.of("b", 2)
            );

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(2, parsedObjects.size());
            assertEquals(expectedResults, parsedObjects);
        }

        @Test
        @DisplayName("test stream parse invalid JSON chunks returns empty")
        @SuppressWarnings("unchecked")
        void testStreamParseInvalidJsonChunks() {
            List<String> chunks = List.of(
                    "```json\n",
                    "{\"data\": ",
                    "\"value\" \n",  // Missing closing brace
                    "```"
            );

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertTrue(parsedObjects.isEmpty());
        }

        @Test
        @DisplayName("test stream parse mixed content and JSON")
        @SuppressWarnings("unchecked")
        void testStreamParseMixedContentAndJson() {
            List<String> chunks = List.of(
                    "Hello world. ",
                    "```json\n{\"key\":",
                    "\"value\"}\n```",
                    " End of message."
            );
            Map<String, Object> expectedResult = Map.of("key", "value");

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(expectedResult, parsedObjects.get(0));
        }

        @Test
        @DisplayName("test stream parse AssistantMessageChunk")
        @SuppressWarnings("unchecked")
        void testStreamParseAiMessageChunks() {
            List<AssistantMessageChunk> chunks = List.of(
                    AssistantMessageChunk.builder().content("```json\n{\"status\":").build(),
                    AssistantMessageChunk.builder().content("\"ok\"}\n```").build()
            );
            Map<String, Object> expectedResult = Map.of("status", "ok");

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(expectedResult, parsedObjects.get(0));
        }

        @Test
        @DisplayName("test stream parse direct JSON without markdown")
        @SuppressWarnings("unchecked")
        void testStreamParseDirectJsonWithoutMarkdown() {
            List<String> chunks = List.of(
                    "{\"direct\":",
                    "\"json\"}"
            );
            Map<String, Object> expectedResult = Map.of("direct", "json");

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            assertEquals(expectedResult, parsedObjects.get(0));
        }

        @Test
        @DisplayName("test stream parse empty chunks returns empty")
        @SuppressWarnings("unchecked")
        void testStreamParseEmptyChunks() {
            List<String> chunks = List.of("", null, "");

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertTrue(parsedObjects.isEmpty());
        }

        @Test
        @DisplayName("test stream parse complex JSON chunks")
        @SuppressWarnings("unchecked")
        void testStreamParseComplexJsonChunks() {
            List<String> chunks = List.of(
                    "```json\n{",
                    "\"users\":[",
                    "{\"id\":1,\"name\":\"Alice\"},",
                    "{\"id\":2,\"name\":\"Bob\"}",
                    "],\"total\":2",
                    "}\n```"
            );

            List<Object> parsedObjects = new ArrayList<>();
            parser.streamParse(chunks.iterator()).forEachRemaining(parsedObjects::add);

            assertEquals(1, parsedObjects.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
            
            assertNotNull(result.get("users"));
            assertNotNull(result.get("total"));
            assertEquals(2, result.get("total"));
        }
    }
}
