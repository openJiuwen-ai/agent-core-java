/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for Python message chunk tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/foundation/llm/test_message_chunk.py}
 * in {@code tests/unit_tests/core/foundation/llm/test_message_chunk.py}.</p>
 */
class MessageChunkPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_merge_parser_content_both_none",
            "test_merge_parser_content_left_none",
            "test_merge_parser_content_right_none",
            "test_merge_parser_content_strings",
            "test_merge_parser_content_lists",
            "test_merge_parser_content_dicts",
            "test_merge_parser_content_dict_string_concat",
            "test_merge_dicts_empty",
            "test_merge_dicts_simple",
            "test_merge_dicts_string_concat",
            "test_merge_dicts_list_concat",
            "test_merge_dicts_nested",
            "test_merge_dicts_overwrite",
            "test_merge_pydantic_models_tool_call",
            "test_merge_pydantic_models_different_types",
            "test_assistant_add_merges_content_strings",
            "test_assistant_add_merges_content_lists",
            "test_assistant_add_merges_tool_calls_with_same_id",
            "test_assistant_add_appends_different_tool_calls",
            "test_assistant_add_merges_tool_calls_without_id",
            "test_assistant_add_copies_tool_call_with_index",
            "test_assistant_add_merges_reasoning_content",
            "test_assistant_add_handles_none_reasoning_content",
            "test_assistant_add_merges_finish_reason",
            "test_assistant_add_ignores_null_finish_reason",
            "test_assistant_add_raises_type_error_for_mismatched_types",
            "test_tool_add_merges_content",
            "test_tool_add_preserves_tool_call_id",
            "test_tool_add_handles_none_content"
    );

    @TestFactory
    Collection<DynamicTest> pythonMessageChunkCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.startsWith("test_merge_parser_content")) {
            assertMergeParserContent(name);
            return;
        }
        if (name.startsWith("test_merge_dicts")) {
            assertMergeDicts(name);
            return;
        }
        if (name.startsWith("test_merge_pydantic_models")) {
            assertMergePydanticModels(name);
            return;
        }
        if (name.startsWith("test_tool_add")) {
            assertToolChunkMerge(name);
            return;
        }
        assertAssistantChunkMerge(name);
    }

    private static void assertMergeParserContent(String name) {
        if (name.endsWith("both_none")) {
            assertThat(MessageChunkMerge.mergeParserContent(null, null)).isNull();
            return;
        }
        if (name.endsWith("left_none")) {
            assertThat(MessageChunkMerge.mergeParserContent(null, "right")).isEqualTo("right");
            return;
        }
        if (name.endsWith("right_none")) {
            assertThat(MessageChunkMerge.mergeParserContent("left", null)).isEqualTo("left");
            return;
        }
        if (name.endsWith("strings")) {
            assertThat(MessageChunkMerge.mergeParserContent("left", "right")).isEqualTo("leftright");
            return;
        }
        if (name.endsWith("lists")) {
            assertThat(MessageChunkMerge.mergeParserContent(List.of(1, 2), List.of(3, 4)))
                    .isEqualTo(List.of(1, 2, 3, 4));
            return;
        }
        if (name.endsWith("dicts")) {
            assertThat(MessageChunkMerge.mergeParserContent(map("a", "1"), map("b", "2")))
                    .isEqualTo(map("a", "1", "b", "2"));
            return;
        }
        assertThat(MessageChunkMerge.mergeParserContent(map("key", "left"), map("key", "right")))
                .isEqualTo(map("key", "leftright"));
    }

    private static void assertMergeDicts(String name) {
        if (name.endsWith("empty")) {
            assertThat(MessageChunkMerge.mergeDicts(map(), map("a", 1))).isEqualTo(map("a", 1));
            return;
        }
        if (name.endsWith("simple")) {
            assertThat(MessageChunkMerge.mergeDicts(map("a", 1), map("b", 2))).isEqualTo(map("a", 1, "b", 2));
            return;
        }
        if (name.endsWith("string_concat")) {
            assertThat(MessageChunkMerge.mergeDicts(map("key", "left"), map("key", "right")))
                    .isEqualTo(map("key", "leftright"));
            return;
        }
        if (name.endsWith("list_concat")) {
            assertThat(MessageChunkMerge.mergeDicts(map("key", List.of(1, 2)), map("key", List.of(3, 4))))
                    .isEqualTo(map("key", List.of(1, 2, 3, 4)));
            return;
        }
        if (name.endsWith("nested")) {
            assertThat(MessageChunkMerge.mergeDicts(
                    map("outer", map("inner", "left")),
                    map("outer", map("inner", "right"))
            )).isEqualTo(map("outer", map("inner", "leftright")));
            return;
        }
        assertThat(MessageChunkMerge.mergeDicts(map("key", "string"), map("key", 123)))
                .isEqualTo(map("key", 123));
    }

    private static void assertMergePydanticModels(String name) {
        ToolCall left = toolCall("call_1", "func", "{", 0);
        if (name.endsWith("different_types")) {
            Map<String, Object> right = map("not", "a_tool_call");
            Object result = MessageChunkMerge.mergePydanticModels(left, right);
            assertThat(result).isSameAs(right);
            return;
        }

        ToolCall right = toolCall("call_1", "", "\"x\": 1}", null);
        Object result = MessageChunkMerge.mergePydanticModels(left, right);

        assertThat(result).isInstanceOf(ToolCall.class);
        ToolCall merged = (ToolCall) result;
        assertThat(merged.getId()).isEqualTo("call_1call_1");
        assertThat(merged.getName()).isEqualTo("func");
        assertThat(merged.getArguments()).isEqualTo("{\"x\": 1}");
        assertThat(merged.getIndex()).isEqualTo(0);
    }

    private static void assertAssistantChunkMerge(String name) {
        if (name.endsWith("content_strings")) {
            AssistantMessageChunk merged = assistant("Hello ", null, null, null, null)
                    .merge(assistant("world!", null, null, null, null));
            assertThat(merged.getContentAsString()).isEqualTo("Hello world!");
            return;
        }
        if (name.endsWith("content_lists")) {
            Map<String, Object> content1 = map("type", "text", "text", "Hello");
            Map<String, Object> content2 = map("type", "text", "text", "world");
            AssistantMessageChunk merged = assistant(List.of(content1), null, null, null, null)
                    .merge(assistant(List.of(content2), null, null, null, null));
            assertThat(merged.getContentAsList()).isEqualTo(List.of(content1, content2));
            return;
        }
        if (name.endsWith("same_id")) {
            AssistantMessageChunk merged = assistant("", List.of(toolCall("call_1", "func", "{", 0)), null, null, null)
                    .merge(assistant("", List.of(toolCall("call_1", "", "\"x\": 1}", null)), null, null, null));
            assertThat(merged.getToolCalls()).hasSize(1);
            assertThat(merged.getToolCalls().getFirst().getId()).isEqualTo("call_1");
            assertThat(merged.getToolCalls().getFirst().getName()).isEqualTo("func");
            assertThat(merged.getToolCalls().getFirst().getArguments()).isEqualTo("{\"x\": 1}");
            assertThat(merged.getToolCalls().getFirst().getIndex()).isEqualTo(0);
            return;
        }
        if (name.endsWith("different_tool_calls")) {
            AssistantMessageChunk merged = assistant("", List.of(toolCall("call_1", "func1", "{}", 0)), null, null, null)
                    .merge(assistant("", List.of(toolCall("call_2", "func2", "{}", 1)), null, null, null));
            assertThat(merged.getToolCalls()).extracting(ToolCall::getId).containsExactly("call_1", "call_2");
            return;
        }
        if (name.endsWith("without_id")) {
            AssistantMessageChunk merged = assistant("", List.of(toolCall(null, "func", "{", 0)), null, null, null)
                    .merge(assistant("", List.of(toolCall(null, "", "\"x\": 1}", null)), null, null, null));
            assertThat(merged.getToolCalls()).hasSize(1);
            assertThat(merged.getToolCalls().getFirst().getName()).isEqualTo("func");
            assertThat(merged.getToolCalls().getFirst().getArguments()).isEqualTo("{\"x\": 1}");
            assertThat(merged.getToolCalls().getFirst().getIndex()).isEqualTo(0);
            return;
        }
        if (name.endsWith("with_index")) {
            AssistantMessageChunk merged = assistant("", List.of(toolCall("call_1", "func", "{}", 5)), null, null, null)
                    .merge(assistant("", null, null, null, null));
            assertThat(merged.getToolCalls()).singleElement().extracting(ToolCall::getIndex).isEqualTo(5);
            return;
        }
        if (name.endsWith("reasoning_content")) {
            AssistantMessageChunk merged = assistant("", null, "Thinking step 1", null, null)
                    .merge(assistant("", null, "Thinking step 2", null, null));
            assertThat(merged.getReasoningContent()).isEqualTo("Thinking step 1Thinking step 2");
            return;
        }
        if (name.endsWith("none_reasoning_content")) {
            AssistantMessageChunk merged = assistant("", null, null, null, null)
                    .merge(assistant("", null, "Some reasoning", null, null));
            assertThat(merged.getReasoningContent()).isEqualTo("Some reasoning");
            return;
        }
        if (name.endsWith("finish_reason")) {
            AssistantMessageChunk merged = assistant("", null, null, "null", null)
                    .merge(assistant("", null, null, "stop", null));
            assertThat(merged.getFinishReason()).isEqualTo("stop");
            return;
        }
        if (name.endsWith("null_finish_reason")) {
            AssistantMessageChunk merged = assistant("", null, null, "stop", null)
                    .merge(assistant("", null, null, "null", null));
            assertThat(merged.getFinishReason()).isEqualTo("stop");
            return;
        }
        assertThatThrownBy(() -> assistant("Hello", null, null, null, null).merge("not a chunk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot merge AssistantMessageChunk");
    }

    private static void assertToolChunkMerge(String name) {
        if (name.endsWith("merges_content")) {
            ToolMessageChunk merged = tool("Partial ", "call_123").merge(tool("result", "call_123"));
            assertThat(merged.getContentAsString()).isEqualTo("Partial result");
            return;
        }
        if (name.endsWith("preserves_tool_call_id")) {
            ToolMessageChunk merged = tool("", "call_123").merge(tool("result", ""));
            assertThat(merged.getToolCallId()).isEqualTo("call_123");
            return;
        }
        ToolMessageChunk merged = tool("", "call_123").merge(tool("result", "call_123"));
        assertThat(merged.getContentAsString()).isEqualTo("result");
    }

    private static AssistantMessageChunk assistant(
            Object content,
            List<ToolCall> toolCalls,
            String reasoningContent,
            String finishReason,
            Object parserContent
    ) {
        return AssistantMessageChunk.builder()
                .role("assistant")
                .content(content)
                .toolCalls(toolCalls)
                .reasoningContent(reasoningContent)
                .finishReason(finishReason)
                .parserContent(parserContent)
                .build();
    }

    private static ToolMessageChunk tool(String content, String toolCallId) {
        return ToolMessageChunk.builder()
                .role("tool")
                .content(content)
                .toolCallId(toolCallId)
                .build();
    }

    private static ToolCall toolCall(String id, String name, String arguments, Integer index) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .index(index)
                .build();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
