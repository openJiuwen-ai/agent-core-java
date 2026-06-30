/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicroCompactProcessorTest {

    @Test
    void triggerRequiresCompletedApiRoundAndClearableToolMessages() {
        MicroCompactProcessorConfig config = MicroCompactProcessorConfig.builder()
                .triggerThreshold(1)
                .keepRecentPerTool(0)
                .build();
        MicroCompactProcessor processor = new MicroCompactProcessor(config);
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null);

        context.addMessages(List.of(
                new UserMessage("u1"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder().id("tc-1").name("grep").arguments("{}").build()))
                        .build(),
                new ToolMessage("result-1", "tc-1", "grep"),
                new UserMessage("u2"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder().id("tc-2").name("grep").arguments("{}").build()))
                        .build(),
                new ToolMessage("result-2", "tc-2", "grep")
        ));

        assertTrue(processor.triggerAddMessages(context, List.of()));
    }

    @Test
    void onAddMessagesClearsOldToolResultsForCompactableTools() {
        MicroCompactProcessorConfig config = MicroCompactProcessorConfig.builder()
                .triggerThreshold(1)
                .keepRecentPerTool(1)
                .build();
        MicroCompactProcessor processor = new MicroCompactProcessor(config);
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null);

        context.addMessages(List.of(
                new UserMessage("u1"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder().id("tc-1").name("grep").arguments("{}").build()))
                        .build(),
                new ToolMessage("old-1", "tc-1", "grep"),
                new UserMessage("u2"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder().id("tc-2").name("grep").arguments("{}").build()))
                        .build(),
                new ToolMessage("old-2", "tc-2", "grep"),
                new UserMessage("u3"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder().id("tc-3").name("grep").arguments("{}").build()))
                        .build(),
                new ToolMessage("keep", "tc-3", "grep")
        ));

        ContextProcessor.ProcessResult result = processor.onAddMessages(context, List.of());
        assertEquals(List.of(), result.messages());
        List<BaseMessage> updated = context.getMessages();
        assertEquals("[Old tool result content cleared]", updated.get(2).getContentAsString());
        assertEquals("[Old tool result content cleared]", updated.get(5).getContentAsString());
        assertEquals("keep", updated.get(8).getContentAsString());
    }

    @Test
    void onAddMessagesReturnsNoopWhenNothingToClear() {
        MicroCompactProcessor processor = new MicroCompactProcessor(MicroCompactProcessorConfig.builder().build());
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null);
        context.addMessages(List.of(
                new UserMessage("u1"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder().id("tc-1").name("grep").arguments("{}").build()))
                        .build(),
                new ToolMessage("recent", "tc-1", "grep")
        ));

        ContextProcessor.ProcessResult result = processor.onAddMessages(context, List.of());
        assertTrue(result.messages().isEmpty());
        assertFalse(context.getMessages().isEmpty());
    }
}
