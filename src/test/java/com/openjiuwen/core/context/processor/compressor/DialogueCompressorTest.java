/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DialogueCompressor}.
 */
class DialogueCompressorTest {

    private static List<ToolCall> createToolCallList(List<String> ids) {
        return ids.stream()
                .map(id -> ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build())
                .toList();
    }

    @Test
    @DisplayName("trigger_add_messages returns true when message count exceeds threshold")
    void triggerAddMessagesReturnsTrueAboveThreshold() {
        com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor compressor =
                new com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor(
                        DialogueCompressorConfig.builder()
                                .messagesThreshold(2)
                                .keepLastRound(false)
                                .build());
        com.openjiuwen.core.context.ModelContext wrappedContext =
                new com.openjiuwen.core.context.ContextEngine(ContextEngineConfig.builder().build())
                        .createContext("test", null, null, List.of(new AssistantMessage("A")), null);
        com.openjiuwen.core.context_engine.context.SessionModelContext context =
                (com.openjiuwen.core.context_engine.context.SessionModelContext) wrappedContext.unwrap();

        assertTrue(compressor.triggerAddMessages(context, List.of(new AssistantMessage("B"), new AssistantMessage("C")), Map.of())
                .toCompletableFuture().join());
    }

    @Test
    @DisplayName("trigger_add_messages returns false below threshold")
    void triggerAddMessagesReturnsFalseBelowThreshold() {
        com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor compressor =
                new com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor(
                        DialogueCompressorConfig.builder()
                                .messagesThreshold(100)
                                .keepLastRound(false)
                                .build());
        com.openjiuwen.core.context.ModelContext wrappedContext =
                new com.openjiuwen.core.context.ContextEngine(ContextEngineConfig.builder().build())
                        .createContext("test", null, null, List.of(), null);
        com.openjiuwen.core.context_engine.context.SessionModelContext context =
                (com.openjiuwen.core.context_engine.context.SessionModelContext) wrappedContext.unwrap();

        assertFalse(compressor.triggerAddMessages(context, List.of(new AssistantMessage("B")), Map.of())
                .toCompletableFuture().join());
    }

    @Test
    @DisplayName("messages_to_keep below threshold prevents compression")
    void messagesToKeepBelowThresholdPreventsCompression() {
        com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor compressor =
                new com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor(
                        DialogueCompressorConfig.builder()
                                .tokensThreshold(1)
                                .messagesToKeep(15)
                                .keepLastRound(false)
                                .build());
        com.openjiuwen.core.context.ModelContext wrappedContext =
                new com.openjiuwen.core.context.ContextEngine(ContextEngineConfig.builder().build())
                        .createContext("test", null, null, List.of(new UserMessage("u1")), tokenCounter(20000));
        com.openjiuwen.core.context_engine.context.SessionModelContext context =
                (com.openjiuwen.core.context_engine.context.SessionModelContext) wrappedContext.unwrap();

        assertFalse(compressor.triggerAddMessages(context, List.of(
                AssistantMessage.builder().content("a1").toolCalls(createToolCallList(List.of("tc-1"))).build(),
                new ToolMessage("t1", "tc-1"),
                new AssistantMessage("a2")), Map.of()).toCompletableFuture().join());
    }

    @Test
    @DisplayName("config defaults match Python current config")
    void configDefaultsMatchPythonCurrentConfig() {
        DialogueCompressorConfig config = DialogueCompressorConfig.builder()
                .messagesThreshold(10)
                .tokensThreshold(5000)
                .messagesToKeep(3)
                .keepLastRound(true)
                .customCompressionPrompt("Custom prompt")
                .compressionTargetTokens(3000)
                .build();

        assertEquals(10, config.getMessagesThreshold());
        assertEquals(5000, config.getTokensThreshold());
        assertEquals(3, config.getMessagesToKeep());
        assertTrue(config.isKeepLastRound());
        assertEquals("Custom prompt", config.getCustomCompressionPrompt());
        assertEquals(3000, config.getCompressionTargetTokens());

        DialogueCompressorConfig defaults = DialogueCompressorConfig.builder().build();
        assertEquals(10000, defaults.getTokensThreshold());
        assertEquals(1800, defaults.getCompressionTargetTokens());
    }

    @Test
    @DisplayName("processor type returns correct name and state is stateless")
    void processorTypeAndStateAreStable() {
        com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor compressor =
                new com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor(
                        DialogueCompressorConfig.builder().build());

        assertEquals("DialogueCompressor", compressor.processorType());
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(Map.of());
    }

    @Test
    @DisplayName("DIALOGUE_MEMORY_BLOCK_MARKER constant is accessible")
    void dialogueMemoryBlockMarkerIsAccessible() {
        assertTrue(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER.startsWith("[DIALOGUE_MEMORY_BLOCK]"));
    }

    private static com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort tokenCounter(int returnValue) {
        return messages -> returnValue;
    }
}
