/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanContextTest {

    @AfterEach
    void tearDown() {
        SpanContext.resetAll();
    }

    @Test
    void llmSpanStatePeekDoesNotPopAndChunkSequenceIncrements() {
        LlmSpanState first = new LlmSpanState("span-a", 10L, "token-a", null, 0);
        LlmSpanState second = new LlmSpanState("span-b", 20L, "token-b", null, 2);

        SpanContext.pushLlmSpanState(first);
        SpanContext.pushLlmSpanState(second);

        assertThat(SpanContext.popLlmSpanState(true)).isSameAs(second);
        assertThat(second.nextChunkSeq()).isEqualTo(3);
        assertThat(SpanContext.popLlmSpanState()).isSameAs(second);
        assertThat(SpanContext.popLlmSpanState()).isSameAs(first);
        assertThat(SpanContext.popLlmSpanState()).isNull();
    }

    @Test
    void toolSpansAreTrackedPerToolNameAsLifoStacks() {
        SpanContext.pushToolSpan("search", "span-1");
        SpanContext.pushToolSpan("search", "span-2");
        SpanContext.pushToolSpan("exec", "span-3");

        assertThat(SpanContext.<String>popToolSpan("search")).isEqualTo("span-2");
        assertThat(SpanContext.<String>popToolSpan("search")).isEqualTo("span-1");
        assertThat(SpanContext.<String>popToolSpan("search")).isNull();
        assertThat(SpanContext.<String>popToolSpan("exec")).isEqualTo("span-3");
    }

    @Test
    void agentSpansCanBeResetBetweenCases() {
        SpanContext.pushAgentSpan("agent-a", "span-a");
        SpanContext.pushAgentSpan("agent-a", "span-b");

        assertThat(SpanContext.<String>popAgentSpan("agent-a")).isEqualTo("span-b");

        SpanContext.resetAll();

        assertThat(SpanContext.popLlmSpanState()).isNull();
        assertThat(SpanContext.<String>popToolSpan("tool")).isNull();
        assertThat(SpanContext.<String>popAgentSpan("agent-a")).isNull();
    }
}
