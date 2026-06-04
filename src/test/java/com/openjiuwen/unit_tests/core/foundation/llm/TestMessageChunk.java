/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.MergeUtils;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessageChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for message chunk merge helpers and chunk aggregation behavior.
 *
 * <p>Mirrors Python's tests/unit_tests/core/foundation/llm/test_message_chunk.py.</p>
 */
@DisplayName("TestMessageChunk")
class TestMessageChunk {

    @Nested
    @DisplayName("mergeParserContent tests")
    class MergeParserContentTests {

        @Test
        void testMergeParserContentBothNone() {
            assertNull(MergeUtils.mergeParserContent(null, null));
        }

        @Test
        void testMergeParserContentLeftNone() {
            assertEquals("right", MergeUtils.mergeParserContent(null, "right"));
        }

        @Test
        void testMergeParserContentRightNone() {
            assertEquals("left", MergeUtils.mergeParserContent("left", null));
        }

        @Test
        void testMergeParserContentStrings() {
            assertEquals("leftright", MergeUtils.mergeParserContent("left", "right"));
        }

        @Test
        void testMergeParserContentLists() {
            assertEquals(List.of(1, 2, 3, 4),
                    MergeUtils.mergeParserContent(List.of(1, 2), List.of(3, 4)));
        }

        @Test
        void testMergeParserContentDicts() {
            assertEquals(Map.of("a", "1", "b", "2"),
                    MergeUtils.mergeParserContent(Map.of("a", "1"), Map.of("b", "2")));
        }

        @Test
        void testMergeParserContentDictStringConcat() {
            assertEquals(Map.of("key", "leftright"),
                    MergeUtils.mergeParserContent(Map.of("key", "left"), Map.of("key", "right")));
        }
    }

    @Nested
    @DisplayName("mergeMaps tests")
    class MergeMapsTests {

        @Test
        void testMergeDictsEmpty() {
            assertEquals(Map.of("a", 1), MergeUtils.mergeMaps(Map.of(), Map.of("a", 1)));
        }

        @Test
        void testMergeDictsSimple() {
            assertEquals(Map.of("a", 1, "b", 2),
                    MergeUtils.mergeMaps(Map.of("a", 1), Map.of("b", 2)));
        }

        @Test
        void testMergeDictsStringConcat() {
            assertEquals(Map.of("key", "leftright"),
                    MergeUtils.mergeMaps(Map.of("key", "left"), Map.of("key", "right")));
        }

        @Test
        void testMergeDictsListConcat() {
            Map<String, Object> merged = MergeUtils.mergeMaps(
                    new LinkedHashMap<>(Map.of("key", new ArrayList<>(List.of(1, 2)))),
                    new LinkedHashMap<>(Map.of("key", new ArrayList<>(List.of(3, 4)))));
            assertEquals(List.of(1, 2, 3, 4), merged.get("key"));
        }

        @Test
        void testMergeDictsNested() {
            Map<String, Object> merged = MergeUtils.mergeMaps(
                    new LinkedHashMap<>(Map.of("outer", new LinkedHashMap<>(Map.of("inner", "left")))),
                    new LinkedHashMap<>(Map.of("outer", new LinkedHashMap<>(Map.of("inner", "right")))));
            @SuppressWarnings("unchecked")
            Map<String, Object> outer = (Map<String, Object>) merged.get("outer");
            assertEquals("leftright", outer.get("inner"));
        }

        @Test
        void testMergeDictsOverwrite() {
            assertEquals(123, MergeUtils.mergeMaps(Map.of("key", "string"), Map.of("key", 123)).get("key"));
        }
    }

    @Nested
    @DisplayName("AssistantMessageChunk merge tests")
    class AssistantChunkTests {

        @Test
        void testAssistantAddMergesContentStrings() {
            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("Hello ")
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("world!")
                            .build());
            assertEquals("Hello world!", result.getContentAsString());
        }

        @Test
        void testAssistantAddMergesContentLists() {
            List<Map<String, Object>> leftContent = List.of(Map.of("type", "text", "text", "Hello"));
            List<Map<String, Object>> rightContent = List.of(Map.of("type", "text", "text", "world"));
            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content(leftContent)
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content(rightContent)
                            .build());
            assertEquals(List.of(leftContent.get(0), rightContent.get(0)), result.getContent());
        }

        @Test
        void testAssistantAddMergesToolCallsWithSameId() {
            ToolCall tc1 = ToolCall.builder().id("call_1").name("func").arguments("{").index(0).build();
            ToolCall tc2 = ToolCall.builder().id("call_1").name("").arguments("\"x\": 1}").build();

            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .toolCalls(List.of(tc1))
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .toolCalls(List.of(tc2))
                            .build());

            assertEquals(1, result.getToolCalls().size());
            assertEquals("call_1", result.getToolCalls().get(0).getId());
            assertEquals("func", result.getToolCalls().get(0).getName());
            assertEquals("{\"x\": 1}", result.getToolCalls().get(0).getArguments());
            assertEquals(0, result.getToolCalls().get(0).getIndex());
        }

        @Test
        void testAssistantAddAppendsDifferentToolCalls() {
            ToolCall tc1 = ToolCall.builder().id("call_1").name("func1").arguments("{}").index(0).build();
            ToolCall tc2 = ToolCall.builder().id("call_2").name("func2").arguments("{}").index(1).build();

            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .toolCalls(List.of(tc1))
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .toolCalls(List.of(tc2))
                            .build());

            assertEquals(2, result.getToolCalls().size());
            assertEquals("call_1", result.getToolCalls().get(0).getId());
            assertEquals("call_2", result.getToolCalls().get(1).getId());
        }

        @Test
        void testAssistantAddMergesToolCallsWithoutId() {
            ToolCall tc1 = ToolCall.builder().id(null).name("func").arguments("{").index(0).build();
            ToolCall tc2 = ToolCall.builder().id(null).name("").arguments("\"x\": 1}").build();

            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .toolCalls(List.of(tc1))
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .toolCalls(List.of(tc2))
                            .build());

            assertEquals(1, result.getToolCalls().size());
            assertEquals("func", result.getToolCalls().get(0).getName());
            assertEquals("{\"x\": 1}", result.getToolCalls().get(0).getArguments());
            assertEquals(0, result.getToolCalls().get(0).getIndex());
        }

        @Test
        void testAssistantAddCopiesToolCallWithIndex() {
            ToolCall tc = ToolCall.builder().id("call_1").name("func").arguments("{}").index(5).build();

            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .toolCalls(List.of(tc))
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .toolCalls(null)
                            .build());

            assertEquals(1, result.getToolCalls().size());
            assertEquals("call_1", result.getToolCalls().get(0).getId());
            assertEquals(5, result.getToolCalls().get(0).getIndex());
        }

        @Test
        void testAssistantAddMergesReasoningContent() {
            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .reasoningContent("Thinking step 1")
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .reasoningContent("Thinking step 2")
                            .build());

            assertEquals("Thinking step 1Thinking step 2", result.getReasoningContent());
        }

        @Test
        void testAssistantAddHandlesNoneReasoningContent() {
            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .reasoningContent(null)
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .reasoningContent("Some reasoning")
                            .build());

            assertEquals("Some reasoning", result.getReasoningContent());
        }

        @Test
        void testAssistantAddMergesFinishReason() {
            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .finishReason("null")
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .finishReason("stop")
                            .build());

            assertEquals("stop", result.getFinishReason());
        }

        @Test
        void testAssistantAddIgnoresNullFinishReason() {
            AssistantMessageChunk result = AssistantMessageChunk.builder()
                    .role("assistant")
                    .content("")
                    .finishReason("stop")
                    .build()
                    .merge(AssistantMessageChunk.builder()
                            .role("assistant")
                            .content("")
                            .finishReason("null")
                            .build());

            assertEquals("stop", result.getFinishReason());
        }
    }

    @Nested
    @DisplayName("ToolMessageChunk merge tests")
    class ToolChunkTests {

        @Test
        void testToolAddMergesContent() {
            ToolMessageChunk result = ToolMessageChunk.builder()
                    .role("tool")
                    .content("Partial ")
                    .toolCallId("call_123")
                    .build()
                    .merge(ToolMessageChunk.builder()
                            .role("tool")
                            .content("result")
                            .toolCallId("call_123")
                            .build());

            assertEquals("Partial result", result.getContentAsString());
        }

        @Test
        void testToolAddPreservesToolCallId() {
            ToolMessageChunk result = ToolMessageChunk.builder()
                    .role("tool")
                    .content("")
                    .toolCallId("call_123")
                    .build()
                    .merge(ToolMessageChunk.builder()
                            .role("tool")
                            .content("result")
                            .toolCallId("")
                            .build());

            assertEquals("call_123", result.getToolCallId());
        }

        @Test
        void testToolAddHandlesNoneContent() {
            ToolMessageChunk result = ToolMessageChunk.builder()
                    .role("tool")
                    .content("")
                    .toolCallId("call_123")
                    .build()
                    .merge(ToolMessageChunk.builder()
                            .role("tool")
                            .content("result")
                            .toolCallId("call_123")
                            .build());

            assertEquals("result", result.getContentAsString());
        }
    }

    @Test
    @DisplayName("mergeParserContent delegates to object merge for same-type POJOs")
    void testMergePydanticModelsToolCall() {
        ToolCall left = ToolCall.builder().id("call_1").name("func").arguments("{").index(0).build();
        ToolCall right = ToolCall.builder().id("call_1").name("").arguments("\"x\": 1}").build();

        Object result = MergeUtils.mergeParserContent(left, right);

        assertInstanceOf(ToolCall.class, result);
        ToolCall merged = (ToolCall) result;
        assertEquals("call_1call_1", merged.getId());
        assertEquals("func", merged.getName());
        assertEquals("{\"x\": 1}", merged.getArguments());
        assertEquals(0, merged.getIndex());
    }
}
