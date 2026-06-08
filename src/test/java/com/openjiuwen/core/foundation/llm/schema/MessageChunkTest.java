/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 */
class MessageChunkTest {

    @Test
    void mergeParserContentHandlesNullsStringsListsAndDicts() {
        assertNull(MessageChunkMerge.mergeParserContent(null, null));
        assertEquals("right", MessageChunkMerge.mergeParserContent(null, "right"));
        assertEquals("left", MessageChunkMerge.mergeParserContent("left", null));
        assertEquals("leftright", MessageChunkMerge.mergeParserContent("left", "right"));
        assertEquals(List.of(1, 2, 3, 4), MessageChunkMerge.mergeParserContent(List.of(1, 2), List.of(3, 4)));
        assertEquals(Map.of("a", "1", "b", "2"), MessageChunkMerge.mergeParserContent(Map.of("a", "1"), Map.of("b", "2")));
        assertEquals(Map.of("key", "leftright"), MessageChunkMerge.mergeParserContent(Map.of("key", "left"), Map.of("key", "right")));
    }

    @Test
    void mergeDictsHandlesNestedCollectionsAndOverwrite() {
        assertEquals(Map.of("a", 1), MessageChunkMerge.mergeDicts(Map.of(), Map.of("a", 1)));
        assertEquals(Map.of("a", 1, "b", 2), MessageChunkMerge.mergeDicts(Map.of("a", 1), Map.of("b", 2)));
        assertEquals(Map.of("key", "leftright"), MessageChunkMerge.mergeDicts(Map.of("key", "left"), Map.of("key", "right")));
        assertEquals(Map.of("key", List.of(1, 2, 3, 4)),
                MessageChunkMerge.mergeDicts(Map.of("key", List.of(1, 2)), Map.of("key", List.of(3, 4))));
        assertEquals(
                Map.of("outer", Map.of("inner", "leftright")),
                MessageChunkMerge.mergeDicts(
                        Map.of("outer", Map.of("inner", "left")),
                        Map.of("outer", Map.of("inner", "right"))
                )
        );
        assertEquals(Map.of("key", 123), MessageChunkMerge.mergeDicts(Map.of("key", "string"), Map.of("key", 123)));
    }

    @Test
    void mergePydanticModelsMergesToolCallFields() {
        ToolCall left = ToolCall.builder().id("call_1").type("function").name("func").arguments("{").index(0).build();
        ToolCall right = ToolCall.builder().id("call_1").type("function").name("").arguments("\"x\": 1}").build();

        Object result = MessageChunkMerge.mergePydanticModels(left, right);

        assertInstanceOf(ToolCall.class, result);
        ToolCall merged = (ToolCall) result;
        assertEquals("call_1call_1", merged.getId());
        assertEquals("func", merged.getName());
        assertEquals("{\"x\": 1}", merged.getArguments());
        assertEquals(0, merged.getIndex());
    }

    @Test
    void assistantChunkMergeHandlesContentToolCallsReasoningAndFinishReason() {
        AssistantMessageChunk left = AssistantMessageChunk.builder()
                .role("assistant")
                .content("Hello ")
                .toolCalls(List.of(ToolCall.builder().id("call_1").type("function").name("func").arguments("{").index(0).build()))
                .reasoningContent("Thinking step 1")
                .finishReason("null")
                .build();
        AssistantMessageChunk right = AssistantMessageChunk.builder()
                .role("assistant")
                .content("world!")
                .toolCalls(List.of(ToolCall.builder().id("call_1").type("function").name("").arguments("\"x\": 1}").build()))
                .reasoningContent("Thinking step 2")
                .finishReason("stop")
                .build();

        AssistantMessageChunk merged = left.merge(right);

        assertEquals("Hello world!", merged.getContentAsString());
        assertEquals(1, merged.getToolCalls().size());
        assertEquals("func", merged.getToolCalls().get(0).getName());
        assertEquals("{\"x\": 1}", merged.getToolCalls().get(0).getArguments());
        assertEquals("Thinking step 1Thinking step 2", merged.getReasoningContent());
        assertEquals("stop", merged.getFinishReason());
    }

    @Test
    void assistantChunkMergeConcatenatesListsAndStreamingFields() {
        AssistantMessageChunk left = AssistantMessageChunk.builder()
                .role("assistant")
                .content(List.of(Map.of("type", "text", "text", "Hello")))
                .promptTokenIds(List.of(1, 2))
                .completionTokenIds(List.of(10))
                .logprobs(new LinkedHashMap<>(Map.of("content", List.of("a"))))
                .build();
        AssistantMessageChunk right = AssistantMessageChunk.builder()
                .role("assistant")
                .content(List.of(Map.of("type", "text", "text", "world")))
                .promptTokenIds(List.of(3, 4))
                .completionTokenIds(List.of(11, 12))
                .logprobs(new LinkedHashMap<>(Map.of("content", List.of("b"))))
                .build();

        AssistantMessageChunk merged = left.merge(right);

        assertEquals(2, merged.getContentAsList().size());
        assertEquals(List.of(1, 2), merged.getPromptTokenIds());
        assertEquals(List.of(10, 11, 12), merged.getCompletionTokenIds());
        assertEquals(Map.of("content", List.of("a", "b")), merged.getLogprobs());
    }

    @Test
    void toolChunkMergeConcatenatesContentAndPreservesToolCallId() {
        ToolMessageChunk merged = ToolMessageChunk.builder()
                .role("tool")
                .content("Partial ")
                .toolCallId("call_123")
                .build()
                .merge(ToolMessageChunk.builder()
                        .role("tool")
                        .content("result")
                        .toolCallId("")
                        .build());

        assertEquals("Partial result", merged.getContentAsString());
        assertEquals("call_123", merged.getToolCallId());
    }
}
