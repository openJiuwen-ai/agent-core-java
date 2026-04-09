/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContextUtils}.
 */
class ContextUtilsTest {

    @Test
    @DisplayName("findLastAiMessageWithoutToolCall finds correct index")
    void testFindLastAiMessage() {
        List<BaseMessage> messages = List.of(
                new UserMessage("hi"),
                new AssistantMessage("hello"),
                new UserMessage("question"),
                AssistantMessage.builder().role("assistant").content("answer").build()
        );

        Optional<Integer> result = ContextUtils.findLastAiMessageWithoutToolCall(messages);
        assertTrue(result.isPresent());
        assertEquals(3, result.get());
    }

    @Test
    @DisplayName("findLastAiMessageWithoutToolCall returns empty when all have tool calls")
    void testFindLastAiMessageWithToolCalls() {
        List<BaseMessage> messages = List.of(
                new UserMessage("hi"),
                AssistantMessage.builder()
                        .role("assistant")
                        .content("calling tool")
                        .toolCalls(List.of(com.openjiuwen.core.foundation.llm.schema.ToolCall.builder()
                                .id("1").name("test").arguments("{}").build()))
                        .build()
        );

        Optional<Integer> result = ContextUtils.findLastAiMessageWithoutToolCall(messages);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("replaceMessages replaces range correctly")
    void testReplaceMessages() {
        List<BaseMessage> msgs = new ArrayList<>(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c"),
                new UserMessage("d")
        ));

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
        List<BaseMessage> messages = List.of(
                new UserMessage("q1"),
                new AssistantMessage("a1"),
                new UserMessage("q2"),
                new AssistantMessage("a2")
        );

        List<int[]> rounds = ContextUtils.findAllDialogueRound(messages);
        assertEquals(2, rounds.size());
    }

    @Test
    @DisplayName("findLastNDialogueRound returns correct start index")
    void testFindLastNDialogueRound() {
        List<BaseMessage> messages = List.of(
                new UserMessage("q1"),
                new AssistantMessage("a1"),
                new UserMessage("q2"),
                new AssistantMessage("a2"),
                new UserMessage("q3"),
                new AssistantMessage("a3")
        );

        int idx = ContextUtils.findLastNDialogueRound(messages, 2);
        assertEquals(2, idx, "Should start from the second dialogue round");
    }

    @Test
    @DisplayName("formatReloadedMessages creates formatted string")
    void testFormatReloadedMessages() {
        List<BaseMessage> messages = List.of(
                new UserMessage("hello"),
                new AssistantMessage("hi there")
        );

        String result = ContextUtils.formatReloadedMessages("handle_123", messages);
        assertTrue(result.contains("handle_123"));
        assertTrue(result.contains("user"));
        assertTrue(result.contains("assistant"));
    }
}
