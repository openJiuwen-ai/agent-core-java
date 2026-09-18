/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/foundation/llm/output_parsers/json_output_parser.py}.
 *
 * <p>Mirrors Python's {@code TestJsonOutputParser} in
 * {@code tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py}.</p>
 */
class JsonOutputParserTest {

    private final JsonOutputParser parser = new JsonOutputParser();

    @Test
    void parseValidJsonString() {
        Object parsed = parse("{\"name\": \"test\", \"value\": 123}");

        assertThat(parsed).isEqualTo(Map.of("name", "test", "value", 123));
    }

    @Test
    void parseValidJsonInMarkdown() {
        String markdownJson = "Here is some info:\n"
                + "```json\n"
                + "{\"item\": \"apple\", \"price\": 1.5}\n"
                + "```\n"
                + "Thanks!";

        Object parsed = parse(markdownJson);

        assertThat(parsed).isEqualTo(Map.of("item", "apple", "price", 1.5));
    }

    @Test
    void parseValidJsonInAssistantMessage() {
        AssistantMessage message = new AssistantMessage("""
                ```json
                {"status": "success", "code": 200}
                ```""");
        message.setUsageMetadata(UsageMetadata.builder().modelName("demo-model").build());

        Object parsed = parse(message);

        assertThat(parsed).isEqualTo(Map.of("status", "success", "code", 200));
    }

    @Test
    void parseInvalidJsonString() {
        Object parsed = parse("{\"name\": \"test\", \"value\": 123,");

        assertThat(parsed).isNull();
    }

    @Test
    void parseNonJsonString() {
        Object parsed = parse("This is just plain text.");

        assertThat(parsed).isNull();
    }

    @Test
    void parseEmptyString() {
        Object parsed = parse("");

        assertThat(parsed).isNull();
    }

    @Test
    void parseNoneInput() {
        Object parsed = parse(null);

        assertThat(parsed).isNull();
    }

    @Test
    void parseComplexJson() {
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

        Object parsed = parse(complexJson);

        assertThat(parsed).isEqualTo(Map.of(
                "users", List.of(
                        Map.of("id", 1, "name", "Alice", "active", true),
                        Map.of("id", 2, "name", "Bob", "active", false)
                ),
                "metadata", Map.of("total", 2, "page", 1)
        ));
    }

    @Test
    void streamParseValidJsonChunks() {
        List<Object> parsedObjects = streamParse(List.of(
                "```json\n",
                "{\"data\": ",
                "\"value\"}\n",
                "```"
        ));

        assertThat(parsedObjects).containsExactly(Map.of("data", "value"));
    }

    @Test
    void streamParseFragmentedJsonChunks() {
        List<Object> parsedObjects = streamParse(List.of(
                "Some text before.\n",
                "```json\n",
                "{\"id\": 1,",
                "\"name\": \"",
                "Fragmented Item\"",
                "}\n",
                "```\n",
                "More text after."
        ));

        assertThat(parsedObjects).containsExactly(Map.of("id", 1, "name", "Fragmented Item"));
    }

    @Test
    void streamParseMultipleJsonObjects() {
        List<Object> parsedObjects = streamParse(List.of(
                "```json\n{\"a\":1}\n```",
                "Some text.",
                "```json\n{\"b\":2}\n```"
        ));

        assertThat(parsedObjects).containsExactly(
                Map.of("a", 1),
                Map.of("b", 2)
        );
    }

    @Test
    void streamParseInvalidJsonChunks() {
        List<Object> parsedObjects = streamParse(List.of(
                "```json\n",
                "{\"data\": ",
                "\"value\" \n",
                "```"
        ));

        assertThat(parsedObjects).isEmpty();
    }

    @Test
    void streamParseMixedContentAndJson() {
        List<Object> parsedObjects = streamParse(List.of(
                "Hello world. ",
                "```json\n{\"key\":",
                "\"value\"}\n```",
                " End of message."
        ));

        assertThat(parsedObjects).containsExactly(Map.of("key", "value"));
    }

    @Test
    void streamParseAssistantMessageChunks() {
        List<AssistantMessageChunk> chunks = List.of(
                AssistantMessageChunk.builder().content("```json\n{\"status\":").build(),
                AssistantMessageChunk.builder()
                        .content("\"ok\"}\n```")
                        .usageMetadata(UsageMetadata.builder().modelName("demo-model").build())
                        .build()
        );

        List<Object> parsedObjects = streamParse(chunks);

        assertThat(parsedObjects).containsExactly(Map.of("status", "ok"));
    }

    @Test
    void streamParseDirectJsonWithoutMarkdown() {
        List<Object> parsedObjects = streamParse(List.of(
                "{\"direct\":",
                "\"json\"}"
        ));

        assertThat(parsedObjects).containsExactly(Map.of("direct", "json"));
    }

    @Test
    void streamParseEmptyChunks() {
        List<Object> chunks = new ArrayList<>();
        chunks.add("");
        chunks.add(null);
        chunks.add("");

        assertThat(streamParse(chunks)).isEmpty();
    }

    @Test
    void streamParseComplexJsonChunks() {
        List<Object> parsedObjects = streamParse(List.of(
                "```json\n{",
                "\"users\":[",
                "{\"id\":1,\"name\":\"Alice\"},",
                "{\"id\":2,\"name\":\"Bob\"}",
                "],\"total\":2",
                "}\n```"
        ));

        assertThat(parsedObjects).containsExactly(Map.of(
                "users", List.of(
                        Map.of("id", 1, "name", "Alice"),
                        Map.of("id", 2, "name", "Bob")
                ),
                "total", 2
        ));
    }

    @Test
    void parseReturnsNullForUnsupportedInputType() {
        Object parsed = parse(42);

        assertThat(parsed).isNull();
    }

    @Test
    void streamParseFlushesRemainingBufferAndSkipsUnsupportedChunks() {
        AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                .content("{\"tail\":3}")
                .build();

        List<Object> outputs = streamParse(List.of(new Object(), chunk));

        assertThat(outputs).containsExactly(Map.of("tail", 3));
    }

    private Object parse(Object input) {
        return parser.parse(input).join();
    }

    private List<Object> streamParse(List<?> chunks) {
        return collect(parser.streamParse(chunks.iterator()));
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> outputs = new ArrayList<>();
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }
        return outputs;
    }
}
