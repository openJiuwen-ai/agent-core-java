/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * With {@code enableTaskLoop=false} the inner ReAct agent must still be invoked exactly once,
 * mirroring Python's {@code _run_single_round_invoke} fallback.
 */
class DeepAgentSingleRoundFallbackTest {

    @TempDir
    private Path tempDir;

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void disabledTaskLoopStillInvokesInnerAgentOnce() {
        CountingAgent inner = new CountingAgent();
        agent = newDisabledTaskLoopAgent(inner);

        Map<String, Object> result = agent.invoke(Map.of("query", "hello"));

        assertThat(inner.invokeCount.get())
                .as("inner ReAct agent must be invoked once when the task loop is disabled")
                .isEqualTo(1);
        assertThat(result).containsEntry("output", "echo:hello");
    }

    /**
     * Control case: the resume branch already routes to the single-round method, so it proves
     * that method works under a disabled task loop.
     */
    @Test
    void disabledTaskLoopResumeBranchReachesInnerAgent() {
        CountingAgent inner = new CountingAgent();
        agent = newDisabledTaskLoopAgent(inner);

        Map<String, Object> result = agent.invoke(Map.of("query", resumeQuery()));

        assertThat(inner.invokeCount.get()).isEqualTo(1);
        assertThat(result).containsKey("output");
    }

    /**
     * Control case for the streaming entry point: a resume input streams through the same
     * single-round method today.
     */
    @Test
    void disabledTaskLoopResumeStreamReachesInnerAgent() {
        CountingAgent inner = new CountingAgent();
        agent = newDisabledTaskLoopAgent(inner);

        List<Object> chunks = new ArrayList<>();
        agent.stream(Map.of("query", resumeQuery()), null, List.of(StreamMode.OUTPUT))
                .forEachRemaining(chunks::add);

        assertThat(inner.invokeCount.get()).isEqualTo(1);
        assertThat(chunks).isNotEmpty();
    }

    @Test
    void disabledTaskLoopStreamForwardsInnerOutput() {
        CountingAgent inner = new CountingAgent();
        agent = newDisabledTaskLoopAgent(inner);

        List<Object> chunks = new ArrayList<>();
        agent.stream(Map.of("query", "hello")).forEachRemaining(chunks::add);

        assertThat(inner.invokeCount.get()).isEqualTo(1);
        assertThat(chunks).isNotEmpty();
    }

    private static InteractiveInput resumeQuery() {
        InteractiveInput query = new InteractiveInput();
        query.update("call-1", "approved");
        return query;
    }

    private DeepAgent newDisabledTaskLoopAgent(Object inner) {
        AgentCard card = AgentCard.builder()
                .id("single-round-" + UUID.randomUUID().toString().replace("-", ""))
                .name("single-round")
                .description("single round fallback")
                .build();
        DeepAgent configured = new DeepAgent(card, DeepAgentConfig.builder()
                .enableTaskLoop(false)
                .workspacePath(tempDir.toString())
                .build(), null);
        configured.setReactAgent(inner, true);
        return configured;
    }

    private static final class CountingAgent {
        private final AtomicInteger invokeCount = new AtomicInteger();

        /**
         * Records the call and echoes the query back.
         *
         * @param inputs invocation inputs
         * @param session session owning the call
         * @return echoed answer
         */
        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            invokeCount.incrementAndGet();
            return CompletableFuture.completedFuture(Map.of(
                    "output", "echo:" + inputs.get("query"),
                    "result_type", "answer"));
        }
    }
}
