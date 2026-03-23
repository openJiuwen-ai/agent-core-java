/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
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
 * Tests for {@link RoundLevelCompressor}.
 * <p>
 * Ported from Python's {@code test_round_level_compressor.py}.
 */
class RoundLevelCompressorTest {

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
                return 0;
            }
        };
    }

    @Test
    @DisplayName("trigger returns true when token count exceeds threshold")
    void testTriggerOnTokenCount() {
        TokenCounter counter = mockTokenCounter(15000);
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder()
                .tokensThreshold(5000)
                .roundsThreshold(3)
                .keepLastRound(false)
                .build();
        RoundLevelCompressor compressor = new RoundLevelCompressor(config);

        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build())
                .createContext("test", null, null, null, counter);
        // Add enough rounds to exceed roundsThreshold=3 (matching Python test)
        for (int i = 0; i < 5; i++) {
            context.addMessages(new UserMessage("User question " + i));
            context.addMessages(new AssistantMessage("Assistant answer " + i));
        }

        List<BaseMessage> newMsgs = List.of(new AssistantMessage("reply"));
        assertTrue(compressor.triggerAddMessages(context, newMsgs));
    }

    @Test
    @DisplayName("trigger returns false when below threshold")
    void testTriggerReturnsFalse() {
        TokenCounter counter = mockTokenCounter(100);
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder()
                .tokensThreshold(50000)
                .keepLastRound(true)
                .build();
        RoundLevelCompressor compressor = new RoundLevelCompressor(config);

        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build())
                .createContext("test", null, null, null, counter);

        assertFalse(compressor.triggerAddMessages(context, List.of(new AssistantMessage("msg2"))));
    }

    @Test
    @DisplayName("config builder sets values correctly")
    void testConfigBuilder() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder()
                .tokensThreshold(5000)
                .keepLastRound(true)
                .customizedCompressionPrompt("Custom prompt")
                .build();

        assertEquals(5000, config.getTokensThreshold());
        assertTrue(config.isKeepLastRound());
        assertEquals("Custom prompt", config.getCustomizedCompressionPrompt());
    }

    @Test
    @DisplayName("processor type returns correct name")
    void testProcessorType() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder().build();
        RoundLevelCompressor compressor = new RoundLevelCompressor(config);
        assertEquals("RoundLevelCompressor", compressor.processorType());
    }

    @Test
    @DisplayName("save/load state is stateless")
    void testSaveLoadState() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder().build();
        RoundLevelCompressor compressor = new RoundLevelCompressor(config);
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(java.util.Map.of());
    }
}
