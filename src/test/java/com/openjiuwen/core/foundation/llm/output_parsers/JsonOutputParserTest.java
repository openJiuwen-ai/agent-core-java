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
 */
class JsonOutputParserTest {

    @Test
    void parseAcceptsAssistantMessageCodeBlock() {
        JsonOutputParser parser = new JsonOutputParser();
        AssistantMessage message = new AssistantMessage("""
                ```json
                {"name":"alice","count":1}
                ```
                """);
        message.setUsageMetadata(UsageMetadata.builder().modelName("demo-model").build());

        Object parsed = parser.parse(message).join();

        assertThat(parsed).isEqualTo(Map.of("name", "alice", "count", 1));
    }

    @Test
    void parseReturnsNullForUnsupportedInputType() {
        JsonOutputParser parser = new JsonOutputParser();

        Object parsed = parser.parse(42).join();

        assertThat(parsed).isNull();
    }

    @Test
    void streamParseYieldsCodeBlockAndDirectJsonObjects() {
        JsonOutputParser parser = new JsonOutputParser();
        AssistantMessageChunk first = AssistantMessageChunk.builder()
                .content("```json\n{\"alpha\":1}")
                .usageMetadata(UsageMetadata.builder().modelName("demo-model").build())
                .build();
        AssistantMessageChunk second = AssistantMessageChunk.builder()
                .content("```")
                .build();

        Iterator<Object> iterator = parser.streamParse(List.of(first, second, "{\"beta\":2}").iterator());
        List<Object> outputs = collect(iterator);

        assertThat(outputs).containsExactly(
                Map.of("alpha", 1),
                Map.of("beta", 2)
        );
    }

    @Test
    void streamParseFlushesRemainingBufferAndSkipsUnsupportedChunks() {
        JsonOutputParser parser = new JsonOutputParser();
        AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                .content("{\"tail\":3}")
                .build();

        Iterator<Object> iterator = parser.streamParse(List.of(new Object(), chunk).iterator());
        List<Object> outputs = collect(iterator);

        assertThat(outputs).containsExactly(Map.of("tail", 3));
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> outputs = new ArrayList<>();
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }
        return outputs;
    }
}
