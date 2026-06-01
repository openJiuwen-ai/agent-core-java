/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DialogueCompressor}.
 * <p>
 * Mirrors Python's {@code test_dialogue_compressor.py} in
 * {@code tests.unit_tests.core.context_engine.test_dialogue_compressor}.
 */
class DialogueCompressorTest {

    private static List<ToolCall> createToolCallList(List<String> ids) {
        return ids.stream()
                .map(id -> ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build())
                .toList();
    }

    private static TokenCounter mockTokenCounter(int returnValue) {
        return new TokenCounter() {
            @Override
            public int count(String text, String model) {
                return returnValue;
            }

            @Override
            public int countMessages(List<BaseMessage> messages, String model) {
                return returnValue;
            }

            @Override
            public int countTools(List<ToolInfo> tools, String model) {
                return tools == null ? 0 : tools.size() * returnValue;
            }
        };
    }

    private ModelContext createContextWithCompressor(
            DialogueCompressorConfig config,
            TokenCounter tokenCounter) {

        ContextEngineConfig engineConfig = ContextEngineConfig.builder()
                .defaultWindowMessageNum(100)
                .build();
        ContextEngine.registerProcessor("DialogueCompressor", DialogueCompressor.class,
                cfg -> new DialogueCompressor((DialogueCompressorConfig) cfg));
        ContextEngine engine = new ContextEngine(engineConfig);
        return engine.createContext("test_ctx", null,
                List.of(new ContextEngine.ProcessorSpec("DialogueCompressor", config)),
                List.of(), tokenCounter);
    }

    @Test
    @DisplayName("trigger returns true when message count exceeds threshold")
    void testTriggerOnMessageCount() {
        DialogueCompressorConfig config = DialogueCompressorConfig.builder()
                .messagesThreshold(2)
                .tokensThreshold(100000)
                .keepLastRound(false)
                .build();
        DialogueCompressor compressor = new DialogueCompressor(config);

        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build())
                .createContext("test", null);
        context.addMessages(new UserMessage("msg1"));

        List<BaseMessage> newMsgs = List.of(
                new AssistantMessage("msg2"),
                new UserMessage("msg3"));
        assertTrue(compressor.triggerAddMessages(context, newMsgs));
    }

    @Test
    @DisplayName("trigger returns true when token count exceeds threshold")
    void testTriggerOnTokenCount() {
        TokenCounter counter = mockTokenCounter(20000);
        DialogueCompressorConfig config = DialogueCompressorConfig.builder()
                .messagesThreshold(100)
                .tokensThreshold(5000)
                .keepLastRound(false)
                .build();
        DialogueCompressor compressor = new DialogueCompressor(config);

        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build())
                .createContext("test", null, null, null, counter);
        context.addMessages(new UserMessage("msg1"));

        List<BaseMessage> newMsgs = List.of(new AssistantMessage("msg2"));
        assertTrue(compressor.triggerAddMessages(context, newMsgs));
    }

    @Test
    @DisplayName("messages_to_keep below threshold prevents compression")
    void testMessagesToKeepPreventsCompression() {
        TokenCounter counter = mockTokenCounter(20000);
        DialogueCompressorConfig config = DialogueCompressorConfig.builder()
                .tokensThreshold(10000)
                .messagesToKeep(15)
                .keepLastRound(false)
                .build();
        ModelContext ctx = createContextWithCompressor(config, counter);

        List<BaseMessage> msgs = List.of(
                new UserMessage("u1"),
                AssistantMessage.builder().content("a1")
                        .toolCalls(createToolCallList(List.of("tc-1"))).build(),
                new ToolMessage("t1", "tc-1"),
                new AssistantMessage("a2"));
        ctx.addMessages(msgs);

        // With messages_to_keep=15, 4 msgs < 15, so no compression should be triggered
        List<BaseMessage> result = ctx.getMessages();
        assertEquals(4, result.size());
    }

    @Test
    @DisplayName("config builder sets values correctly")
    void testConfigBuilder() {
        DialogueCompressorConfig config = DialogueCompressorConfig.builder()
                .messagesThreshold(10)
                .tokensThreshold(5000)
                .messagesToKeep(3)
                .keepLastRound(true)
                .customizedCompressionPrompt("Custom prompt")
                .build();

        assertEquals(10, config.getMessagesThreshold());
        assertEquals(5000, config.getTokensThreshold());
        assertEquals(3, config.getMessagesToKeep());
        assertTrue(config.isKeepLastRound());
        assertEquals("Custom prompt", config.getCustomizedCompressionPrompt());
    }

    @Test
    @DisplayName("processor type returns correct name")
    void testProcessorType() {
        DialogueCompressorConfig config = DialogueCompressorConfig.builder().build();
        DialogueCompressor compressor = new DialogueCompressor(config);
        assertEquals("DialogueCompressor", compressor.processorType());
    }

    @Test
    @DisplayName("save/load state is stateless")
    void testSaveLoadState() {
        DialogueCompressorConfig config = DialogueCompressorConfig.builder().build();
        DialogueCompressor compressor = new DialogueCompressor(config);
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(java.util.Map.of());
    }
}
