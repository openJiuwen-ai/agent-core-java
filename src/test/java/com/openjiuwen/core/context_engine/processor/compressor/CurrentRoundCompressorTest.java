/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for current-round compression behavior.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
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

    private static CurrentRoundCompressorConfig lowThresholdConfig() {
        CurrentRoundCompressorConfig config = new CurrentRoundCompressorConfig();
        config.setTokensThreshold(1);
        config.setMessagesToKeep(1);
        config.setMinSelectedTokensForCompression(1);
        return config;
    }

    private static Model modelReturning(AssistantMessage message) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(message));
    }
}
