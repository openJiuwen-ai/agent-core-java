/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

/**
 * Tests auto-harness context rail overrides.
 *
 * <p>Mirrors Python's {@code test_context_rail.py} for
 * {@code openjiuwen/auto_harness/rails/context_rail.py}.</p>
 */
class AutoHarnessContextRailTest {

    @Test
    void initKeepsContextProcessors() {
        AutoHarnessContextRail rail = new AutoHarnessContextRail(true);
        TestAgent agent = new TestAgent();

        rail.init(agent);

        assertThat(processorTypes(agent))
                .contains("DialogueCompressor", "MessageSummaryOffloader",
                        "CurrentRoundCompressor", "RoundLevelCompressor");
    }

    @Test
    void beforeModelCallSkipsPromptSectionInjection() {
        AutoHarnessContextRail rail = new AutoHarnessContextRail(true);
        TestAgent agent = new TestAgent();
        rail.init(agent);
        AgentCallbackContext context = new AgentCallbackContext(agent);

        rail.beforeModelCall(context).toCompletableFuture().join();

        assertThat(context.getExtra()).doesNotContainKey("offload_section_enabled");
        assertThat(context.getExtra()).doesNotContainKey("task_state");
    }

    @Test
    void uninitIsNoop() {
        AutoHarnessContextRail rail = new AutoHarnessContextRail(true);
        TestAgent agent = new TestAgent();
        rail.init(agent);
        assertThat(processorTypes(agent)).isNotEmpty();

        rail.uninit(agent);

        assertThat(processorTypes(agent)).isNotEmpty();
    }

    private static List<String> processorTypes(TestAgent agent) {
        List<ContextEngine.ProcessorSpec> processors = agent.react_agent._config.getContextProcessors();
        return processors == null ? List.of() : processors.stream().map(ContextEngine.ProcessorSpec::processorType).toList();
    }

    /**
     * Minimal Java test agent exposing Python-style {@code react_agent._config}.
     *
     * <p>Mirrors Python's {@code SimpleNamespace} agent fixture in
     * {@code openjiuwen/auto_harness/rails/context_rail.py} tests.</p>
     */
    private static final class TestAgent extends BaseAgent {
        private final ReactAgentHolder react_agent = new ReactAgentHolder();

        private TestAgent() {
            super(new AgentCard("test-agent", "test-agent", "test agent"));
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

    /**
     * Holder for the ReAct config field shape read by {@code ContextProcessorRail}.
     *
     * <p>Mirrors Python's {@code react_agent._config} field in
     * {@code openjiuwen/auto_harness/rails/context_rail.py} tests.</p>
     */
    private static final class ReactAgentHolder {
        private final ReActAgentConfig _config = new ReActAgentConfig();
    }
}
