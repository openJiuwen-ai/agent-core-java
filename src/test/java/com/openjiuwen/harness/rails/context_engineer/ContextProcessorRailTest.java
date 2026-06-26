/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ContextProcessorRail} in
 * {@code openjiuwen/harness/rails/context_engineer/context_processor_rail.py}.
 */
class ContextProcessorRailTest {

    @Test
    void initBuildsPresetProcessors() {
        MockAgent agent = new MockAgent();
        ContextProcessorRail rail = new ContextProcessorRail();

        rail.init(agent);

        assertThat(agent.react_agent.config.getContextProcessors())
                .extracting(ContextEngine.ProcessorSpec::processorType)
                .containsExactly("MessageSummaryOffloader", "DialogueCompressor",
                        "CurrentRoundCompressor", "RoundLevelCompressor");
        assertThat(rail.getAllProcessors()).hasSize(4);
    }

    @Test
    void mapOverrideMergesWithPresetConfig() {
        MockAgent agent = new MockAgent();
        ContextProcessorRail rail = new ContextProcessorRail(List.of(
                new ContextEngine.ProcessorSpec("DialogueCompressor", Map.of("tokens_threshold", 12345))
        ));

        rail.init(agent);

        DialogueCompressorConfig config = (DialogueCompressorConfig) agent.react_agent.config
                .getContextProcessors()
                .get(1)
                .config();
        assertThat(config.getTokensThreshold()).isEqualTo(12345);
        assertThat(config.getCompressionTargetTokens()).isEqualTo(1800);
    }

    @Test
    void mapOverrideForMissingPresetIsRejected() {
        assertThatThrownBy(() -> ContextProcessorRail.mergeProcessors(
                List.of(),
                List.of(new ContextEngine.ProcessorSpec("Custom", Map.of("a", "b"))),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Custom");
    }

    @Test
    void callbacksRefreshContextMetadata() {
        ContextProcessorRail rail = new ContextProcessorRail();
        AgentCallbackContext ctx = new AgentCallbackContext();

        rail.beforeModelCall(ctx).toCompletableFuture().join();

        assertThat(ctx.getExtra()).containsEntry("offload_section_enabled", true);
    }

    private static final class MockAgent extends BaseAgent {
        @SuppressWarnings("checkstyle:MemberName")
        private final ReactAgent react_agent = new ReactAgent();

        private MockAgent() {
            super(new AgentCard("agent-1", "agent-1", "Agent"));
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return Collections.emptyIterator();
        }
    }

    private static final class ReactAgent {
        private final ReActAgentConfig config = new ReActAgentConfig();
    }
}
