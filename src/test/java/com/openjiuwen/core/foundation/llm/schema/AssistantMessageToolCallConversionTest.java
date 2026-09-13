/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link AssistantMessage#convertOpenAiToolCalls}.
 */
class AssistantMessageToolCallConversionTest {
    @Test
    void missingIndexDefaultsToNull() {
        List<ToolCall> calls = AssistantMessage.convertOpenAiToolCalls(List.of(
                Map.of("id", "call_1", "type", "function", "function",
                        Map.of("name", "todo_create", "arguments", "{}"))));

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getIndex()).isNull();
        assertThat(calls.get(0).getName()).isEqualTo("todo_create");
    }

    @Test
    void presentIndexIsPreserved() {
        List<ToolCall> calls = AssistantMessage.convertOpenAiToolCalls(List.of(
                Map.of("id", "call_1", "type", "function", "index", 2, "function",
                        Map.of("name", "web_search", "arguments", "{\"q\":\"x\"}"))));

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getIndex()).isEqualTo(2);
        assertThat(calls.get(0).getName()).isEqualTo("web_search");
    }

    @Test
    void missingIndexDefaultsToNullForFlatFormat() {
        List<ToolCall> calls = AssistantMessage.convertOpenAiToolCalls(List.of(
                Map.of("id", "call_2", "type", "function", "name", "echo", "arguments", "hi")));

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getIndex()).isNull();
        assertThat(calls.get(0).getName()).isEqualTo("echo");
    }
}
