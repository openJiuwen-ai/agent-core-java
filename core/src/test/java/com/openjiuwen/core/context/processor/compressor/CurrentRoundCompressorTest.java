/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for current-round compression behavior.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
 *
 * <p>Mirrors Python's {@code TestCurrentRoundCompressor} in
 * {@code tests/unit_tests/core/context_engine/test_current_round_compressor.py}.</p>
 */
class CurrentRoundCompressorTest {

    @Test
    void addMessagesCompressesCompletedApiRoundAndEmitsCompactSummaryAndUsage() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        AssistantMessage modelResponse = new AssistantMessage("compressed tool result");
        modelResponse.setUsageMetadata(UsageMetadata.builder()
                .inputTokens(2)
                .outputTokens(3)
                .totalTokens(5)
                .modelName("unit-model")
                .build());
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(config, modelReturning(modelResponse));
        SessionModelContext context = new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                List.of(),
                List.of(compressor),
                messages -> messages.stream().mapToInt(message -> message.getContentAsString().length()).sum());

        List<BaseMessage> messages = List.of(
                new UserMessage("question"),
                AssistantMessage.builder()
                        .role("assistant")
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-1")
                                .name("tool")
                                .type("function")
                                .arguments("{}")
                                .build()))
                        .build(),
                new ToolMessage("large tool result ".repeat(20), "call-1"),
                new AssistantMessage("final answer"));

        context.addMessages(messages).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).hasSize(3);
        assertThat(result.get(1)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1).getContentAsString())
                .startsWith(CurrentRoundCompressor.SUMMARY_MARKER)
                .contains("compressed tool result");
        assertThat(context.compressionHistory()).last()
                .extracting(state -> state.get("compact_summary"))
                .asString()
                .contains(CurrentRoundCompressor.SUMMARY_MARKER)
                .contains("compressed tool result");
        Map<?, ?> usage = (Map<?, ?>) context.compressionHistory()
                .get(context.compressionHistory().size() - 1)
                .get("compression_usage");
        assertThat(((Number) usage.get("calls")).longValue()).isEqualTo(1L);
        assertThat(((Number) usage.get("input_tokens")).longValue()).isEqualTo(2L);
        assertThat(((Number) usage.get("output_tokens")).longValue()).isEqualTo(3L);
        assertThat(((Number) usage.get("total_tokens")).longValue()).isEqualTo(5L);
    }

    @Test
    void multiCompressMergesContiguousSummaryBlocksAndUnwrapsNestedMemory() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        config.setAccumulatedSummaryTokenLimit(1);
        config.setSummaryMergeMinBlocks(3);
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(config,
                modelReturning(new AssistantMessage(CurrentRoundCompressor.SUMMARY_MARKER
                        + "\nSummary:\nmerged memory")));
        SessionModelContext context = new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                List.of(),
                List.of(compressor),
                messages -> 100);
        List<BaseMessage> messages = new ArrayList<>(List.of(
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\nSummary:\none"),
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\nSummary:\ntwo"),
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\nSummary:\nthree"),
                new UserMessage("latest question"),
                new AssistantMessage("latest answer")));

        CurrentRoundCompressor.MultiCompressResult result = compressor.multiCompress(messages, 3, 2, context);

        assertThat(result.messages()).hasSize(3);
        assertThat(result.modifiedIndices()).containsExactly(0, 1, 2);
        assertThat(result.compactSummary())
                .contains(CurrentRoundCompressor.SUMMARY_MARKER)
                .contains("merged memory")
                .doesNotContain("Summary:\n[CURRENT_ROUND_MEMORY_BLOCK]");
    }

    @Test
    void utilityFindsLastCompletedRoundBeforeIncompleteToolCall() {
        List<BaseMessage> messages = List.of(
                new UserMessage("question"),
                new AssistantMessage("prefix"),
                AssistantMessage.builder()
                        .role("assistant")
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-1")
                                .name("tool")
                                .type("function")
                                .arguments("{}")
                                .build()))
                        .build());

        assertThat(CompressorUtils.findLastCompletedApiRoundEndIdx(messages, 1, 2)).isEqualTo(1);
    }

    @Test
    void pythonParityLargeMessageCompressionTriggered() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("Compressed: tool execution result.")));
        SessionModelContext context = newContext(compressor, CurrentRoundCompressorTest::sumContentLength);
        String largeContent = "large tool execution result ".repeat(30);

        context.addMessages(List.of(
                new UserMessage("First message"),
                assistantWithToolCalls("tc-1", "_add_2025"),
                new ToolMessage(largeContent, "tc-1"),
                assistantWithToolCalls("tc-2", "_add_2025"))).toCompletableFuture().join();

        assertThat(context.getMessages())
                .filteredOn(message -> message instanceof UserMessage
                        && message.getContentAsString().contains(CurrentRoundCompressor.SUMMARY_MARKER))
                .hasSize(1);
    }

    @Test
    void pythonParityStreamsStateWhenCurrentRoundCompressorTriggers() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("Compressed: tool execution result.")));
        SessionModelContext context = newContext(compressor, CurrentRoundCompressorTest::sumContentLength);

        context.addMessages(List.of(
                new UserMessage("First message".repeat(100)),
                assistantWithToolCalls("tc-stream", "_add_2025"),
                new ToolMessage("large tool result1 ".repeat(300), "tc-stream"),
                new AssistantMessage("done1"),
                assistantWithToolCalls("tc-stream-2", "_add_2025"),
                new ToolMessage("large tool result2 ".repeat(300), "tc-stream-2"),
                new AssistantMessage("done2"))).toCompletableFuture().join();

        List<Map<String, Object>> states = context.compressionHistory();
        assertThat(states).hasSizeGreaterThanOrEqualTo(2);
        assertThat(states.get(states.size() - 2))
                .containsEntry("status", "started")
                .containsEntry("processor", "CurrentRoundCompressor");
        assertThat(states.get(states.size() - 1))
                .containsEntry("status", "completed")
                .containsEntry("processor", "CurrentRoundCompressor")
                .extracting(state -> state.get("compact_summary"))
                .asString()
                .contains(CurrentRoundCompressor.SUMMARY_MARKER)
                .contains("Compressed: tool execution result.");
    }

    @Test
    void pythonParityCompressionWithAssistantAndToolMessages() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("Through _add_2025 tool, obtained: result is -6.")));
        SessionModelContext context = newContext(compressor, CurrentRoundCompressorTest::sumContentLength);
        String largeContent = "tool result is -6 ".repeat(30);

        context.addMessages(List.of(
                new UserMessage("Calculate 10 + 20"),
                assistantWithToolCalls("tc-1", "_add_2025"),
                new ToolMessage(largeContent, "tc-1"),
                assistantWithToolCalls("tc-2", "_add_2025"),
                new ToolMessage(largeContent, "tc-2"),
                new AssistantMessage("The answer is -6."))).toCompletableFuture().join();

        assertThat(context.getMessages())
                .filteredOn(message -> message instanceof UserMessage
                        && message.getContentAsString().contains(CurrentRoundCompressor.SUMMARY_MARKER))
                .hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void pythonParityCompressionWithMultiAssistantAndToolMessages() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("Through _add_2025 tool, obtained: result is -6.")));
        SessionModelContext context = newContext(compressor, CurrentRoundCompressorTest::sumContentLength);
        String largeContent = "multi tool result is -6 ".repeat(30);

        context.addMessages(List.of(
                new UserMessage("Calculate 10 + 20"),
                assistantWithToolCalls("tc-1", "_add_2025"),
                new ToolMessage(largeContent, "tc-1"),
                assistantWithToolCalls("tc-2", "_add_2025"),
                new ToolMessage(largeContent, "tc-2"),
                new AssistantMessage("The answer is -6."))).toCompletableFuture().join();

        assertThat(context.getMessages())
                .filteredOn(message -> message instanceof UserMessage
                        && message.getContentAsString().contains(CurrentRoundCompressor.SUMMARY_MARKER))
                .hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void pythonParityNoCompressionBelowThreshold() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        config.setTokensThreshold(1_000);
        AtomicInteger modelCalls = new AtomicInteger();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("should not be used"), modelCalls));
        SessionModelContext context = newContext(compressor, messages -> 10);

        context.addMessages(List.of(
                new UserMessage("Short message"),
                new AssistantMessage("Short response"))).toCompletableFuture().join();

        assertThat(context.getMessages()).hasSize(2);
        assertThat(context.getMessages())
                .noneMatch(message -> message instanceof UserMessage
                        && message.getContentAsString().contains(CurrentRoundCompressor.SUMMARY_MARKER));
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void pythonParityNoCompressionWhenUserMessageIsLast() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        AtomicInteger modelCalls = new AtomicInteger();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("should not be used"), modelCalls));
        SessionModelContext context = newContext(compressor, messages -> 100);

        context.addMessages(List.of(
                new UserMessage("First message"),
                new AssistantMessage("Response"),
                new UserMessage("Last message is user"))).toCompletableFuture().join();

        assertThat(context.getMessages()).hasSize(3);
        assertThat(context.getMessages())
                .noneMatch(message -> message instanceof UserMessage
                        && message.getContentAsString().contains(CurrentRoundCompressor.SUMMARY_MARKER));
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void pythonParityMultiCompressReplacesSelectedSpanWithMemoryBlock() {
        CurrentRoundCompressorConfig config = lowThresholdConfig();
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(
                config, modelReturning(new AssistantMessage("compressed")));
        SessionModelContext context = newContext(compressor, CurrentRoundCompressorTest::sumContentLength);
        List<BaseMessage> contextMessages = List.of(
                new UserMessage("question"),
                new AssistantMessage("safe-prefix-1"),
                new AssistantMessage("safe-prefix-2"),
                assistantWithToolCalls("tc-1", "tool_a"),
                new ToolMessage("tool result", "tc-1"),
                new AssistantMessage("final answer"));

        CurrentRoundCompressor.MultiCompressResult result = compressor.multiCompress(contextMessages, 0, 3, context);

        assertThat(result.messages()).isNotNull();
        assertThat(result.messages()).hasSize(5);
        assertThat(result.messages().get(1)).isInstanceOf(UserMessage.class);
        assertThat(result.messages().get(1).getContentAsString())
                .contains(CurrentRoundCompressor.SUMMARY_MARKER)
                .contains("compressed");
        assertThat(((AssistantMessage) result.messages().get(2)).getToolCalls().get(0).getId()).isEqualTo("tc-1");
        assertThat(((ToolMessage) result.messages().get(3)).getToolCallId()).isEqualTo("tc-1");
        assertThat(result.modifiedIndices()).containsExactly(1, 2);
        assertThat(result.compactSummary()).contains(CurrentRoundCompressor.SUMMARY_MARKER).contains("compressed");
    }

    private static CurrentRoundCompressorConfig lowThresholdConfig() {
        CurrentRoundCompressorConfig config = new CurrentRoundCompressorConfig();
        config.setTokensThreshold(1);
        config.setMessagesToKeep(1);
        config.setMinSelectedTokensForCompression(1);
        return config;
    }

    private static SessionModelContext newContext(CurrentRoundCompressor compressor,
                                                  ModelContext.TokenCounterPort tokenCounter) {
        return new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                List.of(),
                List.of(compressor),
                tokenCounter);
    }

    private static int sumContentLength(List<BaseMessage> messages) {
        return messages.stream().mapToInt(message -> message.getContentAsString().length()).sum();
    }

    private static AssistantMessage assistantWithToolCalls(String toolCallId, String toolName) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(toolCallId)
                        .name(toolName)
                        .type("function")
                        .arguments("")
                        .build()))
                .build();
    }

    private static Model modelReturning(AssistantMessage message) {
        return modelReturning(message, new AtomicInteger());
    }

    private static Model modelReturning(AssistantMessage message, AtomicInteger invocationCount) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(recordInvocationAndReturn(invocationCount, message)));
    }

    private static AssistantMessage recordInvocationAndReturn(AtomicInteger invocationCount, AssistantMessage message) {
        invocationCount.incrementAndGet();
        return message;
    }
}
