/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code ContextProcessorRail} in
 * {@code openjiuwen/harness/rails/context_engineer/context_processor_rail.py}.
 */
class ContextProcessorRailTest {

    @Test
    void directReActAgentInstallsAndRemovesPresetProcessors() {
        ReActAgent agent = new ReActAgent(new AgentCard("direct-react", "direct-react", "Agent"));
        ReActAgentConfig config = new ReActAgentConfig();
        agent.configure(config);
        ContextProcessorRail rail = new ContextProcessorRail();

        agent.registerRail(rail).toCompletableFuture().join();
        try {
            assertThat(config.getContextProcessors())
                    .extracting(ContextEngine.ProcessorSpec::processorType)
                    .containsExactly("MessageSummaryOffloader", "DialogueCompressor",
                            "CurrentRoundCompressor", "RoundLevelCompressor");
            assertThat(rail.getAllProcessors()).hasSize(4);
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }

        assertThat(config.getContextProcessors()).isEmpty();
        assertThat(rail.getAllProcessors()).isEmpty();
    }

    @Test
    void directReActAgentAppliesUserProcessorsWithoutPresets() {
        ReActAgent agent = new ReActAgent(new AgentCard("custom-react", "custom-react", "Agent"));
        ReActAgentConfig reactConfig = new ReActAgentConfig();
        agent.configure(reactConfig);
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        ContextProcessorRail rail = new ContextProcessorRail(List.of(
                new ContextEngine.ProcessorSpec("DialogueCompressor", config)), false, null);

        agent.registerRail(rail).toCompletableFuture().join();
        try {
            assertThat(reactConfig.getContextProcessors())
                    .extracting(ContextEngine.ProcessorSpec::processorType)
                    .containsExactly("DialogueCompressor");
            assertThat(rail.getAllProcessors()).hasSize(1);
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

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

        rail.beforeModelCall(ctx);

        assertThat(ctx.getExtra()).containsEntry("offload_section_enabled", true);
    }

    @Test
    void onModelExceptionRequestsRetryAfterOverflowCompression() {
        ContextEngine.registerProcessor("T00919CompactProcessor", RecordingCompactProcessor::new);
        ReActAgent agent = new ReActAgent(new AgentCard("overflow-recover", "overflow-recover", "Agent"));
        ReActAgentConfig config = new ReActAgentConfig();
        ContextEngine.ProcessorSpec compactSpec =
                new ContextEngine.ProcessorSpec("T00919CompactProcessor", new Object());
        config.setContextProcessors(List.of(compactSpec));
        agent.configure(config);

        ContextProcessorRail rail = new ContextProcessorRail(List.of(compactSpec), false, null);
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            AgentSessionApi session = new MemorySession("overflow-session");
            ModelContext modelContext = agent.initContext(session);

            AgentCallbackContext ctx = new AgentCallbackContext(agent);
            ctx.setSession(session);
            ctx.setContext(modelContext);
            ctx.setException(new RuntimeException("maximum context length exceeded"));

            rail.onModelException(ctx);

            assertThat(ctx.consumeRetryRequest()).isNotNull();
            assertThat(ctx.getExtra())
                    .containsEntry(ContextProcessorRail.MODEL_EXCEPTION_RECOVERY_ATTEMPTED_KEY, Boolean.TRUE);

            ctx.setException(new RuntimeException("maximum context length exceeded"));
            rail.onModelException(ctx);
            assertThat(ctx.consumeRetryRequest()).isNull();
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void onModelExceptionSkipsRetryWhenStreamChunksAlreadyEmitted() {
        ContextEngine.registerProcessor("T00919CompactProcessor", RecordingCompactProcessor::new);
        ReActAgent agent = new ReActAgent(new AgentCard("overflow-stream", "overflow-stream", "Agent"));
        ReActAgentConfig config = new ReActAgentConfig();
        ContextEngine.ProcessorSpec compactSpec =
                new ContextEngine.ProcessorSpec("T00919CompactProcessor", new Object());
        config.setContextProcessors(List.of(compactSpec));
        agent.configure(config);

        ContextProcessorRail rail = new ContextProcessorRail(List.of(compactSpec), false, null);
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            AgentSessionApi session = new MemorySession("overflow-stream-session");
            ModelContext modelContext = agent.initContext(session);

            AgentCallbackContext ctx = new AgentCallbackContext(agent);
            ctx.setSession(session);
            ctx.setContext(modelContext);
            ctx.getExtra().put("_streaming", Boolean.TRUE);
            ctx.getExtra().put(ContextProcessorRail.STREAM_CHUNKS_EMITTED_KEY, 1);
            ctx.setException(new RuntimeException("maximum context length exceeded"));

            rail.onModelException(ctx);

            assertThat(ctx.consumeRetryRequest()).isNull();
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
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
        public Object invoke(Object inputs, AgentSessionApi session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return Collections.emptyIterator();
        }
    }

    private static final class ReactAgent {
        private final ReActAgentConfig config = new ReActAgentConfig();
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }

    private static final class RecordingCompactProcessor implements SessionModelContext.ContextProcessorPort {
        private RecordingCompactProcessor(Object config) {
        }

        @Override
        public String processorType() {
            return "T00919CompactProcessor";
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                    new CompactEvent(),
                    messages,
                    null
            ));
        }
    }

    private static final class CompactEvent implements SessionModelContext.ContextProcessorEventPort {
        @Override
        public String compactSummary() {
            return "recovered by test processor";
        }
    }
}
