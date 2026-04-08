/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.output_parsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonOutputParser.
 * Ported from Python: tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py
 */
class JsonOutputParserTest {

    private JsonOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonOutputParser();
    }

    // ============================== Parse tests ==============================

    @Nested
    @DisplayName("parse() method tests")
    class ParseTests {

        @Test
        @DisplayName("Parse valid JSON string")
        void testParseValidJsonString() {
            String jsonStr = "{\"name\": \"test\", \"value\": 123}";
            Object result = parser.parse(jsonStr);
            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("test", map.get("name"));
            assertEquals(123, map.get("value"));
        }

        @Test
        @DisplayName("Parse valid JSON in markdown code block")
        void testParseValidJsonInMarkdown() {
            String markdownJson = "Here is some info:\n```json\n{\"item\": \"apple\", \"price\": 1.5}\n```\nThanks!";
            Object result = parser.parse(markdownJson);
            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("apple", map.get("item"));
            assertEquals(1.5, map.get("price"));
        }

        @Test
        @DisplayName("Parse valid JSON in AssistantMessage")
        void testParseValidJsonInAssistantMessage() {
            AssistantMessage aiMessage = AssistantMessage.builder()
                    .content("```json\n{\"status\": \"success\", \"code\": 200}\n```")
                    .build();
            Object result = parser.parse(aiMessage);
            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("success", map.get("status"));
            assertEquals(200, map.get("code"));
        }

        @Test
        @DisplayName("Parse invalid JSON string returns null")
        void testParseInvalidJsonString() {
            String invalidJson = "{\"name\": \"test\", \"value\": 123,";
            Object result = parser.parse(invalidJson);
            assertNull(result);
        }

        @Test
        @DisplayName("Parse non-JSON text returns null")
        void testParseNonJsonString() {
            String nonJson = "This is just plain text.";
            Object result = parser.parse(nonJson);
            assertNull(result);
        }

        @Test
        @DisplayName("Parse empty string returns null")
        void testParseEmptyString() {
            Object result = parser.parse("");
            assertNull(result);
        }

        @Test
        @DisplayName("Parse null input returns null")
        void testParseNullInput() {
            Object result = parser.parse(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Parse complex JSON structure")
        @SuppressWarnings("unchecked")
        void testParseComplexJson() {
            String complexJson = """
                    ```json
                    {
                        "users": [
                            {"id": 1, "name": "Alice", "active": true},
                            {"id": 2, "name": "Bob", "active": false}
                        ],
                        "metadata": {
                            "total": 2,
                            "page": 1
                        }
                    }
                    ```""";
            Object result = parser.parse(complexJson);
            assertNotNull(result);
            Map<String, Object> map = (Map<String, Object>) result;

            List<Map<String, Object>> users = (List<Map<String, Object>>) map.get("users");
            assertEquals(2, users.size());
            assertEquals("Alice", users.get(0).get("name"));
            assertEquals(true, users.get(0).get("active"));
            assertEquals("Bob", users.get(1).get("name"));
            assertEquals(false, users.get(1).get("active"));

            Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
            assertEquals(2, metadata.get("total"));
            assertEquals(1, metadata.get("page"));
        }
    }

    // ============================== Stream parse tests ==============================

    @Nested
    @DisplayName("streamParse() method tests")
    class StreamParseTests {

        @Test
        @DisplayName("Stream parse valid JSON chunks")
        @SuppressWarnings("unchecked")
        void testStreamParseValidJsonChunks() {
            List<String> chunks = List.of(
                    "```json\n",
                    "{\"data\": ",
                    "\"value\"}\n",
                    "```"
            );

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(1, parsedObjects.size());
            Map<String, Object> map = (Map<String, Object>) parsedObjects.get(0);
            assertEquals("value", map.get("data"));
        }

        @Test
        @DisplayName("Stream parse fragmented JSON chunks")
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

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(1, parsedObjects.size());
            Map<String, Object> map = (Map<String, Object>) parsedObjects.get(0);
            assertEquals(1, map.get("id"));
            assertEquals("Fragmented Item", map.get("name"));
        }

        @Test
        @DisplayName("Stream parse multiple JSON objects")
        @SuppressWarnings("unchecked")
        void testStreamParseMultipleJsonObjects() {
            List<String> chunks = List.of(
                    "```json\n{\"a\":1}\n```",
                    "Some text.",
                    "```json\n{\"b\":2}\n```"
            );

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(2, parsedObjects.size());
            assertEquals(1, ((Map<String, Object>) parsedObjects.get(0)).get("a"));
            assertEquals(2, ((Map<String, Object>) parsedObjects.get(1)).get("b"));
        }

        @Test
        @DisplayName("Stream parse invalid JSON chunks yields empty")
        void testStreamParseInvalidJsonChunks() {
            List<String> chunks = List.of(
                    "```json\n",
                    "{\"data\": ",
                    "\"value\" \n",  // Missing closing brace
                    "```"
            );

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(0, parsedObjects.size());
        }

        @Test
        @DisplayName("Stream parse mixed content and JSON")
        @SuppressWarnings("unchecked")
        void testStreamParseMixedContentAndJson() {
            List<String> chunks = List.of(
                    "Hello world. ",
                    "```json\n{\"key\":",
                    "\"value\"}\n```",
                    " End of message."
            );

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(1, parsedObjects.size());
            Map<String, Object> map = (Map<String, Object>) parsedObjects.get(0);
            assertEquals("value", map.get("key"));
        }

        @Test
        @DisplayName("Stream parse AssistantMessageChunk objects")
        @SuppressWarnings("unchecked")
        void testStreamParseAssistantMessageChunks() {
            List<Object> chunks = List.of(
                    AssistantMessageChunk.builder().content("```json\n{\"status\":").build(),
                    AssistantMessageChunk.builder().content("\"ok\"}\n```").build()
            );

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(1, parsedObjects.size());
            Map<String, Object> map = (Map<String, Object>) parsedObjects.get(0);
            assertEquals("ok", map.get("status"));
        }

        @Test
        @DisplayName("Stream parse direct JSON without markdown")
        @SuppressWarnings("unchecked")
        void testStreamParseDirectJsonWithoutMarkdown() {
            List<String> chunks = List.of(
                    "{\"direct\":",
                    "\"json\"}"
            );

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(1, parsedObjects.size());
            Map<String, Object> map = (Map<String, Object>) parsedObjects.get(0);
            assertEquals("json", map.get("direct"));
        }

        @Test
        @DisplayName("Stream parse empty chunks yields empty")
        void testStreamParseEmptyChunks() {
            List<String> chunks = List.of("", "", "");

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(0, parsedObjects.size());
        }

        @Test
        @DisplayName("Stream parse complex nested JSON chunks")
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

            List<Object> parsedObjects = collectStreamResults(chunks.iterator());

            assertEquals(1, parsedObjects.size());
            Map<String, Object> map = (Map<String, Object>) parsedObjects.get(0);
            List<Map<String, Object>> users = (List<Map<String, Object>>) map.get("users");
            assertEquals(2, users.size());
            assertEquals("Alice", users.get(0).get("name"));
            assertEquals("Bob", users.get(1).get("name"));
            assertEquals(2, map.get("total"));
        }

        /**
         * Collect all results from a stream parse iterator.
         */
        private List<Object> collectStreamResults(Iterator<?> chunks) {
            Iterator<Object> streamIter = parser.streamParse(chunks);
            List<Object> results = new ArrayList<>();
            while (streamIter.hasNext()) {
                results.add(streamIter.next());
            }
            return results;
        }
    }
}
