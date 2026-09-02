/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressor;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests for {@link ContextUtils}.
 */
class ContextUtilsTest {
    @Test
    @DisplayName("findLastAiMessageWithoutToolCall finds correct index")
    void testFindLastAiMessage() {
        List<BaseMessage> messages = List.of(new UserMessage("hi"), new AssistantMessage("hello"),
                new UserMessage("question"), AssistantMessage.builder().role("assistant").content("answer").build());

        Optional<Integer> result = ContextUtils.findLastAiMessageWithoutToolCall(messages);
        assertTrue(result.isPresent());
        assertEquals(3, result.get());
    }

    @Test
    @DisplayName("findLastAiMessageWithoutToolCall returns empty when all have tool calls")
    void testFindLastAiMessageWithToolCalls() {
        List<BaseMessage> messages = List.of(new UserMessage("hi"),
                AssistantMessage.builder().role("assistant").content("calling tool")
                        .toolCalls(List.of(com.openjiuwen.core.foundation.llm.schema.ToolCall.builder().id("1")
                                .name("test").arguments("{}").build()))
                        .build());

        Optional<Integer> result = ContextUtils.findLastAiMessageWithoutToolCall(messages);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("replaceMessages replaces range correctly")
    void testReplaceMessages() {
        List<BaseMessage> msgs = new ArrayList<>(
                List.of(new UserMessage("a"), new UserMessage("b"), new UserMessage("c"), new UserMessage("d")));

        List<BaseMessage> replacements = List.of(new UserMessage("X"));
        List<BaseMessage> result = ContextUtils.replaceMessages(msgs, replacements, 1, 2);

        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getContentAsString());
        assertEquals("X", result.get(1).getContentAsString());
        assertEquals("d", result.get(2).getContentAsString());
    }

    @Test
    @DisplayName("findAllDialogueRound identifies dialogue rounds")
    void testFindAllDialogueRound() {
        List<BaseMessage> messages = List.of(new UserMessage("q1"), new AssistantMessage("a1"), new UserMessage("q2"),
                new AssistantMessage("a2"));

        List<int[]> rounds = ContextUtils.findAllDialogueRound(messages);
        assertEquals(2, rounds.size());
    }

    @Test
    @DisplayName("findAllDialogueRound merges contiguous user blocks and keeps incomplete rounds")
    void testFindAllDialogueRoundMergesUserBlocks() {
        List<BaseMessage> messages = List.of(new UserMessage("q1"), new UserMessage("q1-1"), new AssistantMessage("a1"),
                new UserMessage("q2"), new UserMessage("q2-1"));

        List<int[]> rounds = ContextUtils.findAllDialogueRound(messages);
        assertEquals(2, rounds.size());
        assertArrayEquals(new int[]{3, -1}, rounds.get(0));
        assertArrayEquals(new int[]{0, 2}, rounds.get(1));
    }

    @Test
    @DisplayName("findLastNDialogueRound returns correct start index")
    void testFindLastNDialogueRound() {
        List<BaseMessage> messages = List.of(new UserMessage("q1"), new AssistantMessage("a1"), new UserMessage("q2"),
                new AssistantMessage("a2"), new UserMessage("q3"), new AssistantMessage("a3"));

        int idx = ContextUtils.findLastNDialogueRound(messages, 2);
        assertEquals(2, idx, "Should start from the second dialogue round");
    }

    @Test
    @DisplayName("findLastNDialogueRound treats only-user input as one round")
    void testFindLastNDialogueRoundOnlyUsers() {
        List<BaseMessage> messages = List.of(new UserMessage("q1"), new UserMessage("q2"));

        int idx = ContextUtils.findLastNDialogueRound(messages, 1);
        assertEquals(0, idx);
        assertEquals(1, ContextUtils.findAllDialogueRound(messages).size());
    }

    @Test
    @DisplayName("formatReloadedMessages creates formatted string")
    void testFormatReloadedMessages() {
        List<BaseMessage> messages = List.of(new UserMessage("hello"), new AssistantMessage("hi there"));

        String result = ContextUtils.formatReloadedMessages("handle_123", messages);
        assertTrue(result.contains("handle_123"));
        assertTrue(result.contains("user"));
        assertTrue(result.contains("assistant"));
    }

    @Test
    @DisplayName("resolveToolCallFromMessage finds matching tool call")
    void testResolveToolCallFromMessage() {
        ToolCall toolCall = ToolCall.builder().id("tc-1").name("grep").arguments("{\"path\":\"README.md\"}").build();
        List<BaseMessage> messages =
            List.of(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build(),
                    ToolMessage.builder().content("result").toolCallId("tc-1").name("grep").build());

        ToolCall resolved = ContextUtils.resolveToolCallFromMessage(messages.get(1), messages);
        assertNotNull(resolved);
        assertEquals("grep", resolved.getName());
        assertEquals("grep", ContextUtils.resolveToolNameFromMessage(messages.get(1), messages));
    }

    @Test
    @DisplayName("resolveContextMax prefers explicit fallback then mapping")
    void testResolveContextMax() {
        assertEquals(123, ContextUtils.resolveContextMax("mapped-model", 123, java.util.Map.of("mapped-model", 456)));
        assertEquals(456, ContextUtils.resolveContextMax("mapped-model", null, java.util.Map.of("mapped-model", 456)));
        assertTrue(ContextUtils.resolveContextMax("gpt-4o", null, null) > 0);
        assertEquals(ContextUtils.DEFAULT_CONTEXT_MAX_TOKENS, ContextUtils.resolveContextMax(null, null, null));
    }

    @Test
    @DisplayName("isCompressionProcessor detects compressor types")
    void testIsCompressionProcessor() {
        assertTrue(ContextUtils
                .isCompressionProcessor(new RoundLevelCompressor(RoundLevelCompressorConfig.builder().build())));
        assertFalse(ContextUtils.isCompressionProcessor(new Object()));
    }
}
