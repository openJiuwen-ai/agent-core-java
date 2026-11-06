/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies which stream chunks count as model output, and the shape of the
 * latency fields carried on {@link UsageMetadata}.
 *
 * <p>Mirrors Python's {@code carries_output_token} contract in
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}: role-only,
 * heartbeat, usage-only and finish-only frames must not advance the latency
 * window, otherwise both TTFT and inter-token latency are inflated.</p>
 */
class StreamLatencyMetricsTest {

    @Test
    @DisplayName("content-bearing chunk carries output")
    void contentChunkCarriesOutput() {
        assertTrue(chunk("hello", null, null).carriesOutputToken());
    }

    @Test
    @DisplayName("whitespace content still counts as a generated token")
    void whitespaceContentCarriesOutput() {
        assertTrue(chunk(" ", null, null).carriesOutputToken());
    }

    @Test
    @DisplayName("reasoning-only chunk carries output")
    void reasoningChunkCarriesOutput() {
        assertTrue(chunk(null, "thinking", null).carriesOutputToken());
    }

    @Test
    @DisplayName("role-only and empty frames do not carry output")
    void framingChunksDoNotCarryOutput() {
        assertFalse(chunk(null, null, null).carriesOutputToken());
        assertFalse(chunk("", "", null).carriesOutputToken());
        assertFalse(chunk("", null, List.of()).carriesOutputToken());
    }

    @Test
    @DisplayName("usage-only or finish-only frame does not carry output")
    void usageOnlyChunkDoesNotCarryOutput() {
        AssistantMessageChunk usageOnly = AssistantMessageChunk.builder()
                .content("")
                .finishReason("stop")
                .usageMetadata(UsageMetadata.builder().outputTokens(7).build())
                .build();
        assertFalse(usageOnly.carriesOutputToken());
    }

    @Test
    @DisplayName("tool call fragment counts once it names the tool or extends arguments")
    void toolCallFragmentsCarryOutput() {
        AssistantMessageChunk named = chunk("", null, List.of(ToolCall.builder().name("search").build()));
        assertTrue(named.carriesOutputToken());

        AssistantMessageChunk withArgs = chunk("", null, List.of(ToolCall.builder().arguments("{\"q\":").build()));
        assertTrue(withArgs.carriesOutputToken());

        AssistantMessageChunk idOnly = chunk("", null, List.of(ToolCall.builder().id("call_1").build()));
        assertFalse(idOnly.carriesOutputToken());
    }

    @Test
    @DisplayName("latency fields default to null so absence is distinguishable from zero")
    void latencyFieldsDefaultToNull() {
        UsageMetadata usage = UsageMetadata.builder().build();
        assertNull(usage.getTtftSeconds());
        assertNull(usage.getInterTokenLatencyMs());
    }

    @Test
    @DisplayName("latency fields survive modelDump and are omitted when unset")
    void latencyFieldsAppearInModelDump() {
        UsageMetadata empty = UsageMetadata.builder().build();
        assertFalse(empty.modelDump().containsKey("ttft_seconds"));
        assertFalse(empty.modelDump().containsKey("inter_token_latency_ms"));

        UsageMetadata measured = UsageMetadata.builder()
                .ttftSeconds(0.42d)
                .interTokenLatencyMs(12.5d)
                .build();
        assertEquals(0.42d, measured.modelDump().get("ttft_seconds"));
        assertEquals(12.5d, measured.modelDump().get("inter_token_latency_ms"));
    }

    @Test
    @DisplayName("merged chunk keeps the usage object that carries latency values")
    void mergeKeepsUsageCarryingLatency() {
        AssistantMessageChunk first = chunk("he", null, null);
        UsageMetadata usage = UsageMetadata.builder()
                .outputTokens(4)
                .ttftSeconds(0.3d)
                .interTokenLatencyMs(11.0d)
                .build();
        AssistantMessageChunk last = AssistantMessageChunk.builder()
                .content("llo")
                .usageMetadata(usage)
                .build();

        AssistantMessageChunk merged = first.merge(last);
        assertEquals("hello", merged.getContent());
        assertEquals(0.3d, merged.getUsageMetadata().getTtftSeconds());
        assertEquals(4, merged.getUsageMetadata().getOutputTokens());
    }

    private static AssistantMessageChunk chunk(Object content, String reasoning, List<ToolCall> toolCalls) {
        return AssistantMessageChunk.builder()
                .content(content)
                .reasoningContent(reasoning)
                .toolCalls(toolCalls)
                .build();
    }
}
