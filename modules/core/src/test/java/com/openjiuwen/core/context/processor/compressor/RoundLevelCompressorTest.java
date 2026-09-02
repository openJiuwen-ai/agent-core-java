/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link RoundLevelCompressor}.
 */
class RoundLevelCompressorTest {
    @Test
    @DisplayName("trigger_get_context_window uses trigger_total_tokens")
    void triggerGetContextWindowUsesTriggerTotalTokens() {
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(
                RoundLevelCompressorConfig.builder().triggerTotalTokens(100).targetTotalTokens(50).build());
        ModelContext context = mock(ModelContext.class);
        ContextWindow contextWindow = ContextWindow.builder().systemMessages(List.of())
                .contextMessages(List.of(new UserMessage("u"))).tools(List.of()).build();

        compressor.forcedContextWindowTokens = 75;
        assertFalse(compressor.triggerGetContextWindow(context, contextWindow));

        compressor.forcedContextWindowTokens = 101;
        assertTrue(compressor.triggerGetContextWindow(context, contextWindow));
    }

    @Test
    @DisplayName("build_memory_message returns plain user message")
    void buildMemoryMessageReturnsPlainUserMessage() {
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(
                RoundLevelCompressorConfig.builder().triggerTotalTokens(100).targetTotalTokens(50).build());
        ModelContext context = mock(ModelContext.class);
        RoundLevelCompressor.CompressTarget target = new RoundLevelCompressor.CompressTarget("block_1", "ongoing_react",
                0, 0, List.of(new AssistantMessage("analysis state")), 0, 1, 1);

        BaseMessage message = compressor.buildMemoryMessage("User Requirements:\n- Keep intent.", target, context);

        assertInstanceOf(UserMessage.class, message);
        assertTrue(message.getContentAsString().startsWith(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER));
        assertTrue(message.getContentAsString().contains("processor: RoundLevelCompressor"));
        assertEquals(1, message.getMetadata().get(RoundLevelCompressor.COMPRESS_LEVEL));
    }

    @Test
    @DisplayName("on_get_context_window reports original message range")
    void onGetContextWindowReportsOriginalMessageRange() {
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(
                RoundLevelCompressorConfig.builder().triggerTotalTokens(100).targetTotalTokens(50).build());
        List<BaseMessage> compressed = List.of(new UserMessage(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER + "\n"
                + "processor: RoundLevelCompressor\n" + "Summary:\ncompressed"));
        compressor.compressUntilTargetResult = compressed;
        compressor.forcedContextWindowTokens = 101;
        ModelContext context = mock(ModelContext.class);
        ContextWindow contextWindow = ContextWindow.builder().systemMessages(List.of())
                .contextMessages(List.of(new UserMessage("u".repeat(90)), new AssistantMessage("a".repeat(90)),
                        new UserMessage("x".repeat(90)), new AssistantMessage("y".repeat(90))))
                .tools(List.of()).build();

        ContextProcessor.ProcessResult result = compressor.onGetContextWindow(context, contextWindow);

        assertNotNull(result.event());
        assertEquals(List.of(0, 1, 2, 3), result.event().getMessagesToModify());
        assertEquals(1, result.contextWindow().getContextMessages().size());
        assertTrue(result.contextWindow().getContextMessages().get(0).getContentAsString()
                .startsWith(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER));
        verify(context).setMessages(compressed);
    }

    @Test
    @DisplayName("build_compression_user_prompt includes ongoing and completed requirements")
    void buildCompressionUserPromptIncludesOngoingAndCompletedRequirements() {
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(
                RoundLevelCompressorConfig.builder().triggerTotalTokens(100).targetTotalTokens(50).build());
        ModelContext context = mock(ModelContext.class);
        when(context.tokenCounter()).thenReturn(null);

        String promptText = compressor
                .buildCompressionUserPrompt(
                        List.of(new UserMessage("request"), new AssistantMessage("working"),
                                new UserMessage("another request"), new AssistantMessage("final answer")),
                        List.of(new RoundLevelCompressor.CompressTarget("block_1", "ongoing_react", 0, 1,
                                List.of(new UserMessage("request"), new AssistantMessage("working")), 0, 1, 1),
                                new RoundLevelCompressor.CompressTarget("block_2", "completed_react", 2, 3,
                                        List.of(new UserMessage("another request"),
                                                new AssistantMessage("final answer")),
                                        0, 1, 1)),
                        context, "phase_1", 300, 0, null, null);

        assertTrue(promptText.contains("User Requirements"));
        assertTrue(promptText.contains("Final Result"));
        assertTrue(promptText.contains("Do not weaken or over-compress the user's original request"));
    }

    @Test
    @DisplayName("config defaults match Python current config")
    void configDefaultsMatchPythonCurrentConfig() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder().build();

        assertEquals(230000, config.getTriggerTotalTokens());
        assertEquals(160000, config.getTargetTotalTokens());
        assertEquals(0, config.getKeepRecentMessages());
        assertEquals(250000, config.getCompressionCallMaxTokens());
        assertEquals(30000, config.getFirstPassTargetTokens());
        assertEquals(20000, config.getSecondPassTargetTokens());
        assertEquals(10000, config.getThirdPassTargetTokens());
        assertEquals(0.2, config.getTruncateHeadRatio());
        assertEquals(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER, config.getCompressionMarker());
    }

    @Test
    @DisplayName("processor type returns correct name and state is stateless")
    void processorTypeAndStateAreStable() {
        RoundLevelCompressor compressor = new RoundLevelCompressor(RoundLevelCompressorConfig.builder().build());

        assertEquals("RoundLevelCompressor", compressor.processorType());
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(Map.of());
    }

    private static final class TestableRoundLevelCompressor extends RoundLevelCompressor {
        private Integer forcedContextWindowTokens;
        private List<BaseMessage> compressUntilTargetResult;

        private TestableRoundLevelCompressor(RoundLevelCompressorConfig config) {
            super(config);
        }

        @Override
        int countContextWindowTokens(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages,
                List<ToolInfo> tools, ModelContext context) {
            if (forcedContextWindowTokens != null) {
                return forcedContextWindowTokens;
            }
            return super.countContextWindowTokens(systemMessages, contextMessages, tools, context);
        }

        @Override
        List<BaseMessage> compressUntilTarget(List<BaseMessage> contextMessages, ModelContext context,
                List<BaseMessage> systemMessages, List<ToolInfo> tools, int keepRecent, boolean force) {
            if (compressUntilTargetResult != null) {
                return compressUntilTargetResult;
            }
            return super.compressUntilTarget(contextMessages, context, systemMessages, tools, keepRecent, force);
        }
    }

    @SuppressWarnings("unused")
    private static TokenCounter tokenCounter(int returnValue) {
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
}
