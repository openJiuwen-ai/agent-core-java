/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
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
import java.util.Map;

/**
 * Tests for {@link DialogueCompressor}.
 */
class DialogueCompressorTest {
    private static List<ToolCall> createToolCallList(List<String> ids) {
        return ids.stream()
                .map(id -> ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build()).toList();
    }

    @Test
    @DisplayName("trigger_add_messages uses character fallback without token counter")
    void triggerAddMessagesUsesCharacterFallbackWithoutTokenCounter() {
        DialogueCompressor compressor = new TestableDialogueCompressor(DialogueCompressorConfig.builder()
                .messagesThreshold(100).tokensThreshold(100).keepLastRound(false).build());
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null,
                null, List.of(new AssistantMessage("A".repeat(180))), null);

        assertTrue(compressor.triggerAddMessages(context, List.of(new AssistantMessage("B".repeat(180)))));
    }

    @Test
    @DisplayName("on_add_messages replaces finished round with dialogue memory block")
    void onAddMessagesReplacesFinishedRoundWithMemoryBlock() {
        TestableDialogueCompressor compressor = new TestableDialogueCompressor(
                DialogueCompressorConfig.builder().messagesThreshold(2).keepLastRound(false).build());
        compressor.nextResponse = AssistantMessage.builder().content("")
                .parserContent(Map.of("blocks", List.of(Map.of("block_id", "react_1", "summary", "Final Result: X."))))
                .build();
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null,
                null, List.of(), compressionBenefitTokenCounter());

        ContextProcessor.ProcessResult result =
            compressor.onAddMessages(context,
                    List.of(new UserMessage("Call the tool"),
                            AssistantMessage.builder().content("").toolCalls(createToolCallList(List.of("tc-1")))
                                    .build(),
                            new ToolMessage("Tool result: data", "tc-1"),
                            new AssistantMessage("Based on the result, the answer is X.")));

        assertNotNull(result.event());
        assertEquals(List.of(), result.messages());
        assertEquals(List.of(1, 2, 3), result.event().getMessagesToModify());
        List<BaseMessage> updatedMessages = context.getMessages();
        assertEquals(2, updatedMessages.size());
        assertEquals("Call the tool", updatedMessages.get(0).getContentAsString());
        assertInstanceOf(UserMessage.class, updatedMessages.get(1));
        assertTrue(updatedMessages.get(1).getContentAsString()
                .startsWith(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER));
        assertTrue(updatedMessages.get(1).getContentAsString().contains("Final Result"));
    }

    @Test
    @DisplayName("invalid blocks payload falls back to raw response content")
    void invalidBlocksPayloadFallsBackToRawResponseContent() {
        TestableDialogueCompressor compressor = new TestableDialogueCompressor(
                DialogueCompressorConfig.builder().messagesThreshold(2).keepLastRound(false).build());
        compressor.nextResponse =
            AssistantMessage.builder().content("User Requirements:\n- Keep details.\n\nFinal Result:\n- Done.")
                    .parserContent(Map.of("summary", "old schema")).build();
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null,
                null, List.of(), compressionBenefitTokenCounter());

        ContextProcessor.ProcessResult result = compressor.onAddMessages(context,
                List.of(new UserMessage("u"),
                        AssistantMessage.builder().content("").toolCalls(createToolCallList(List.of("tc-1"))).build(),
                        new ToolMessage("tool output", "tc-1"), new AssistantMessage("final answer")));

        assertNotNull(result.event());
        assertEquals(List.of(1, 2, 3), result.event().getMessagesToModify());
        assertEquals(2, context.getMessages().size());
        assertTrue(context.getMessages().get(1).getContentAsString().contains("Final Result"));
    }

    @Test
    @DisplayName("built-in compression prompt is used as system prompt")
    void builtinCompressionPromptUsedAsSystemPrompt() {
        TestableDialogueCompressor compressor = new TestableDialogueCompressor(DialogueCompressorConfig.builder()
                .messagesThreshold(2).keepLastRound(false).compressionTargetTokens(123).build());
        compressor.nextResponse = AssistantMessage
                .builder().content(
                        "")
                .parserContent(Map.of("blocks", List.of(Map.of("block_id", "react_1", "summary",
                        "User Requirements:\n- Keep details.\n\nFinal Result:\n- Done."))))
                .build();
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null,
                null, List.of(), compressionBenefitTokenCounter());

        compressor.onAddMessages(context,
                List.of(new UserMessage("u"),
                        AssistantMessage.builder().content("").toolCalls(createToolCallList(List.of("tc-1"))).build(),
                        new ToolMessage("tool output", "tc-1"), new AssistantMessage("final answer")));

        assertTrue(compressor.lastModelMessages.get(0).getContentAsString().contains("Task Data Preservation Expert"));
        assertTrue(compressor.lastModelMessages.get(0).getContentAsString().contains("<= 123 tokens"));
        assertTrue(compressor.lastModelMessages.get(1).getContentAsString().contains("[Compression Targets]"));
        assertTrue(compressor.lastModelMessages.get(2).getContentAsString().contains("[Target Mapping]"));
    }

    @Test
    @DisplayName("messages_to_keep below threshold prevents compression")
    void messagesToKeepBelowThresholdPreventsCompression() {
        DialogueCompressor compressor = new TestableDialogueCompressor(
                DialogueCompressorConfig.builder().tokensThreshold(1).messagesToKeep(15).keepLastRound(false).build());
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext("test", null,
                null, List.of(new UserMessage("u1")), tokenCounter(20000));

        assertFalse(compressor.triggerAddMessages(context,
                List.of(AssistantMessage.builder().content("a1").toolCalls(createToolCallList(List.of("tc-1"))).build(),
                        new ToolMessage("t1", "tc-1"), new AssistantMessage("a2"))));
    }

    @Test
    @DisplayName("config defaults match Python current config")
    void configDefaultsMatchPythonCurrentConfig() {
        DialogueCompressorConfig config =
            DialogueCompressorConfig.builder().messagesThreshold(10).tokensThreshold(5000).messagesToKeep(3)
                    .keepLastRound(true).customCompressionPrompt("Custom prompt").compressionTargetTokens(3000).build();

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
        DialogueCompressor compressor = new DialogueCompressor(DialogueCompressorConfig.builder().build());

        assertEquals("DialogueCompressor", compressor.processorType());
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(Map.of());
    }

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

    private static TokenCounter compressionBenefitTokenCounter() {
        return new TokenCounter() {
            @Override
            public int count(String text, String model) {
                return text != null ? text.length() : 0;
            }

            @Override
            public int countMessages(List<BaseMessage> messages, String model) {
                if (messages.size() == 1 && messages.get(0) instanceof UserMessage && messages.get(0)
                        .getContentAsString().startsWith(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER)) {
                    return 1;
                }
                return 100;
            }

            @Override
            public int countTools(List<ToolInfo> tools, String model) {
                return 0;
            }
        };
    }

    private static final class TestableDialogueCompressor extends DialogueCompressor {
        private AssistantMessage nextResponse;
        private List<BaseMessage> lastModelMessages;

        private TestableDialogueCompressor(DialogueCompressorConfig config) {
            super(config);
        }

        @Override
        AssistantMessage invokeMultiBlockCompression(List<BaseMessage> contextMessages, List<CompressTarget> targets) {
            String systemPrompt = buildSystemPrompt();
            lastModelMessages = List.of(new com.openjiuwen.core.foundation.llm.schema.SystemMessage(systemPrompt),
                    new UserMessage(buildSplitContextPayload(contextMessages, targets)),
                    new UserMessage(buildTargetsPayload(targets)));
            return nextResponse;
        }
    }
}
