/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.output_parser;

import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for JSON output parser behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.foundation.output_parser.test_json_output_parser} in
 * {@code tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py}.</p>
 */
class JsonOutputParserPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py";

    @TestFactory
    Collection<DynamicTest> pythonJsonParserCases() {
        return pythonTestNodes()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonTestNodes() {
        return Stream.of(
                SOURCE + "::TestJsonOutputParser::test_parse_valid_json_string",
                SOURCE + "::TestJsonOutputParser::test_parse_valid_json_in_markdown",
                SOURCE + "::TestJsonOutputParser::test_parse_valid_json_in_aimessage",
                SOURCE + "::TestJsonOutputParser::test_parse_invalid_json_string",
                SOURCE + "::TestJsonOutputParser::test_parse_non_json_string",
                SOURCE + "::TestJsonOutputParser::test_parse_empty_string",
                SOURCE + "::TestJsonOutputParser::test_parse_none_input",
                SOURCE + "::TestJsonOutputParser::test_parse_complex_json",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_valid_json_chunks",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_fragmented_json_chunks",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_multiple_json_objects",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_invalid_json_chunks",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_mixed_content_and_json",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_aimessage_chunks",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_direct_json_without_markdown",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_empty_chunks",
                SOURCE + "::TestJsonOutputParser::test_stream_parse_complex_json_chunks"
        );
    }

    private static void runPythonCase(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::TestJsonOutputParser::test_parse_valid_json_string" -> testParseValidJsonString();
            case SOURCE + "::TestJsonOutputParser::test_parse_valid_json_in_markdown" ->
                    testParseValidJsonInMarkdown();
            case SOURCE + "::TestJsonOutputParser::test_parse_valid_json_in_aimessage" ->
                    testParseValidJsonInAssistantMessage();
            case SOURCE + "::TestJsonOutputParser::test_parse_invalid_json_string" -> testParseInvalidJsonString();
            case SOURCE + "::TestJsonOutputParser::test_parse_non_json_string" -> testParseNonJsonString();
            case SOURCE + "::TestJsonOutputParser::test_parse_empty_string" -> testParseEmptyString();
            case SOURCE + "::TestJsonOutputParser::test_parse_none_input" -> testParseNoneInput();
            case SOURCE + "::TestJsonOutputParser::test_parse_complex_json" -> testParseComplexJson();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_valid_json_chunks" ->
                    testStreamParseValidJsonChunks();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_fragmented_json_chunks" ->
                    testStreamParseFragmentedJsonChunks();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_multiple_json_objects" ->
                    testStreamParseMultipleJsonObjects();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_invalid_json_chunks" ->
                    testStreamParseInvalidJsonChunks();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_mixed_content_and_json" ->
                    testStreamParseMixedContentAndJson();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_aimessage_chunks" ->
                    testStreamParseAssistantMessageChunks();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_direct_json_without_markdown" ->
                    testStreamParseDirectJsonWithoutMarkdown();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_empty_chunks" -> testStreamParseEmptyChunks();
            case SOURCE + "::TestJsonOutputParser::test_stream_parse_complex_json_chunks" ->
                    testStreamParseComplexJsonChunks();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testParseValidJsonString() {
        Object result = parser().parse("{\"name\": \"test\", \"value\": 123}").join();

        assertThat(result).isEqualTo(Map.of("name", "test", "value", 123));
    }

    private static void testParseValidJsonInMarkdown() {
        String markdownJson = "Here is some info:\n```json\n{\"item\": \"apple\", \"price\": 1.5}\n```\nThanks!";

        Object result = parser().parse(markdownJson).join();

        assertThat(result).isEqualTo(Map.of("item", "apple", "price", 1.5d));
    }

    private static void testParseValidJsonInAssistantMessage() {
        AssistantMessage message = new AssistantMessage("```json\n{\"status\": \"success\", \"code\": 200}\n```");

        Object result = parser().parse(message).join();

        assertThat(result).isEqualTo(Map.of("status", "success", "code", 200));
    }

    private static void testParseInvalidJsonString() {
        Object result = parser().parse("{\"name\": \"test\", \"value\": 123,").join();

        assertThat(result).isNull();
    }

    private static void testParseNonJsonString() {
        Object result = parser().parse("This is just plain text.").join();

        assertThat(result).isNull();
    }

    private static void testParseEmptyString() {
        Object result = parser().parse("").join();

        assertThat(result).isNull();
    }

    private static void testParseNoneInput() {
        Object result = parser().parse(null).join();

        assertThat(result).isNull();
    }

    private static void testParseComplexJson() {
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
                ```
                """;

        Object result = parser().parse(complexJson).join();

        assertThat(result).isEqualTo(Map.of(
                "users", List.of(
                        Map.of("id", 1, "name", "Alice", "active", true),
                        Map.of("id", 2, "name", "Bob", "active", false)),
                "metadata", Map.of("total", 2, "page", 1)
        ));
    }

    private static void testStreamParseValidJsonChunks() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "```json\n",
                "{\"data\": ",
                "\"value\"}\n",
                "```"
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of("data", "value"));
    }

    private static void testStreamParseFragmentedJsonChunks() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "Some text before.\n",
                "```json\n",
                "{\"id\": 1,",
                "\"name\": \"",
                "Fragmented Item\"",
                "}\n",
                "```\n",
                "More text after."
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of("id", 1, "name", "Fragmented Item"));
    }

    private static void testStreamParseMultipleJsonObjects() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "```json\n{\"a\":1}\n```",
                "Some text.",
                "```json\n{\"b\":2}\n```"
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of("a", 1), Map.of("b", 2));
    }

    private static void testStreamParseInvalidJsonChunks() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "```json\n",
                "{\"data\": ",
                "\"value\" \n",
                "```"
        ).iterator()));

        assertThat(parsedObjects).isEmpty();
    }

    private static void testStreamParseMixedContentAndJson() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "Hello world. ",
                "```json\n{\"key\":",
                "\"value\"}\n```",
                " End of message."
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of("key", "value"));
    }

    private static void testStreamParseAssistantMessageChunks() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                AssistantMessageChunk.builder().content("```json\n{\"status\":").build(),
                AssistantMessageChunk.builder().content("\"ok\"}\n```").build()
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of("status", "ok"));
    }

    private static void testStreamParseDirectJsonWithoutMarkdown() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "{\"direct\":",
                "\"json\"}"
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of("direct", "json"));
    }

    private static void testStreamParseEmptyChunks() {
        List<Object> chunks = new ArrayList<>();
        chunks.add("");
        chunks.add(null);
        chunks.add("");

        List<Object> parsedObjects = collect(parser().streamParse(chunks.iterator()));

        assertThat(parsedObjects).isEmpty();
    }

    private static void testStreamParseComplexJsonChunks() {
        List<Object> parsedObjects = collect(parser().streamParse(List.of(
                "```json\n{",
                "\"users\":[",
                "{\"id\":1,\"name\":\"Alice\"},",
                "{\"id\":2,\"name\":\"Bob\"}",
                "],\"total\":2",
                "}\n```"
        ).iterator()));

        assertThat(parsedObjects).containsExactly(Map.of(
                "users", List.of(
                        Map.of("id", 1, "name", "Alice"),
                        Map.of("id", 2, "name", "Bob")),
                "total", 2
        ));
    }

    private static JsonOutputParser parser() {
        return new JsonOutputParser();
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }
}
