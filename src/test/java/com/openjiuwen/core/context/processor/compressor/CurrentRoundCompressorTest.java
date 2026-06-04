/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMixin;
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
 * Tests for {@link CurrentRoundCompressor}.
 * <p>
 * Mirrors Python's {@code test_current_round_compressor.py} in
 * {@code tests.unit_tests.core.context_engine.test_current_round_compressor}.
 * <p>
 * Note: The Python tests rely on mocking the Model.invoke call for actual LLM compression.
 * In Java we test the trigger logic and structural behaviour without a real LLM endpoint.
 * When the model is not available, the compressor should gracefully skip compression.
 */
class CurrentRoundCompressorTest {

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
            CurrentRoundCompressorConfig config,
            TokenCounter tokenCounter) {

        ContextEngineConfig engineConfig = ContextEngineConfig.builder()
                .defaultWindowMessageNum(100)
                .build();
        ContextEngine.registerProcessor("CurrentRoundCompressor", CurrentRoundCompressor.class,
                cfg -> new CurrentRoundCompressor((CurrentRoundCompressorConfig) cfg));
        ContextEngine engine = new ContextEngine(engineConfig);
        return engine.createContext("test_ctx", null,
                List.of(new ContextEngine.ProcessorSpec("CurrentRoundCompressor", config)),
                List.of(), tokenCounter);
    }

    @Test
    @DisplayName("no compression below threshold - messages remain intact")
    void testNoCompressionBelowThreshold() {
        TokenCounter tokenCounter = mockTokenCounter(10);
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder()
                .messagesThreshold(10)
                .largeMessageThreshold(1000)
                .messagesToKeep(1)
                .build();
        ModelContext ctx = createContextWithCompressor(config, tokenCounter);

        List<BaseMessage> msgs = List.of(
                new UserMessage("Short message"),
                new AssistantMessage("Short response"));
        ctx.addMessages(msgs);

        List<BaseMessage> result = ctx.getMessages();
        assertTrue(result.size() >= 2);
        assertTrue(result.stream().noneMatch(m -> m instanceof OffloadMixin));
    }

    @Test
    @DisplayName("no compression when UserMessage is last")
    void testNoCompressionWhenUserMessageIsLast() {
        TokenCounter tokenCounter = mockTokenCounter(10);
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder()
                .messagesThreshold(10)
                .largeMessageThreshold(10)
                .messagesToKeep(1)
                .build();
        ModelContext ctx = createContextWithCompressor(config, tokenCounter);

        List<BaseMessage> msgs = List.of(
                new UserMessage("First message"),
                new AssistantMessage("Response"),
                new UserMessage("Last message is user"));
        ctx.addMessages(msgs);

        List<BaseMessage> result = ctx.getMessages();
        assertTrue(result.size() >= 3);
    }

    @Test
    @DisplayName("trigger returns true when message count exceeds threshold")
    void testTriggerOnMessageCount() {
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder()
                .messagesThreshold(2)
                .tokensThreshold(100000)
                .largeMessageThreshold(50)
                .messagesToKeep(1)
                .build();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(config);

        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build())
                .createContext("test", null);
        context.addMessages(new UserMessage("msg1"));

        List<BaseMessage> newMsgs = List.of(
                new AssistantMessage("msg2"),
                new UserMessage("msg3"));

        assertTrue(compressor.triggerAddMessages(context, newMsgs));
    }

    @Test
    @DisplayName("trigger returns false when below threshold")
    void testTriggerReturnsFalse() {
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder()
                .messagesThreshold(100)
                .tokensThreshold(100000)
                .largeMessageThreshold(50)
                .build();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(config);

        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build())
                .createContext("test", null);

        List<BaseMessage> newMsgs = List.of(new UserMessage("short"));
        assertFalse(compressor.triggerAddMessages(context, newMsgs));
    }

    @Test
    @DisplayName("config builder sets values correctly")
    void testConfigBuilder() {
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder()
                .messagesThreshold(10)
                .tokensThreshold(5000)
                .largeMessageThreshold(200)
                .messagesToKeep(3)
                .singleMultiCompression(true)
                .customizedCompressionPrompt("Custom prompt")
                .build();

        assertEquals(10, config.getMessagesThreshold());
        assertEquals(5000, config.getTokensThreshold());
        assertEquals(200, config.getLargeMessageThreshold());
        assertEquals(3, config.getMessagesToKeep());
        assertTrue(config.isSingleMultiCompression());
        assertEquals("Custom prompt", config.getCustomizedCompressionPrompt());
    }

    @Test
    @DisplayName("processor type returns correct name")
    void testProcessorType() {
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder().build();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(config);
        assertEquals("CurrentRoundCompressor", compressor.processorType());
    }

    @Test
    @DisplayName("save/load state is stateless")
    void testSaveLoadState() {
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder().build();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(config);
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(java.util.Map.of()); // no-op, should not throw
    }
}
