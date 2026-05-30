/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import com.openjiuwen.auto_harness.rails.ContextRail;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for context rail.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.rails.test_context_rail}.</p>
 */
@DisplayName("Context Rail Tests")
class TestContextRail {
    @Test
    @DisplayName("context rail keeps context processors")
    void testAutoHarnessContextRailInitKeepsContextProcessors() {
        ContextRail rail = new ContextRail(true);
        AgentStub agent = makeAgent();

        rail.init(agent);

        assertInstanceOf(ContextProcessorRail.class, rail);
        Map<String, Object> processors = agent.reactAgent.config.contextProcessors;
        assertTrue(processors.containsKey("DialogueCompressor"));
        assertTrue(processors.containsKey("MessageSummaryOffloader"));
        assertTrue(processors.containsKey("CurrentRoundCompressor"));
        assertTrue(processors.containsKey("RoundLevelCompressor"));
    }

    @Test
    @DisplayName("context rail skips prompt section injection")
    void testAutoHarnessContextRailSkipsPromptSectionInjection() {
        ContextRail rail = new ContextRail(true);
        AgentStub agent = makeAgent();
        rail.init(agent);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .inputs(ModelCallInputs.builder()
                        .messages(List.of(Map.of("role", "user", "content", "test")))
                        .build())
                .build();
        rail.beforeModelCall(ctx);

        assertEquals(0, agent.systemPromptBuilder.addSectionCalls);
        assertEquals(0, agent.systemPromptBuilder.removeSectionCalls);
    }

    @Test
    @DisplayName("context rail uninit is noop")
    void testAutoHarnessContextRailUninitIsNoop() {
        ContextRail rail = new ContextRail(true);
        AgentStub agent = makeAgent();
        rail.init(agent);

        rail.uninit(agent);

        assertEquals(0, agent.systemPromptBuilder.removeSectionCalls);
    }

    private static AgentStub makeAgent() {
        AgentConfigStub config = new AgentConfigStub();
        config.contextProcessors.put("DialogueCompressor", new Object());
        config.contextProcessors.put("MessageSummaryOffloader", new Object());
        config.contextProcessors.put("CurrentRoundCompressor", new Object());
        config.contextProcessors.put("RoundLevelCompressor", new Object());
        return new AgentStub(new ReactAgentStub(config), new SystemPromptBuilderStub(), new Object());
    }

    private static final class AgentStub {
        private final ReactAgentStub reactAgent;
        private final SystemPromptBuilderStub systemPromptBuilder;
        private final Object abilityManager;

        private AgentStub(ReactAgentStub reactAgent, SystemPromptBuilderStub systemPromptBuilder,
                Object abilityManager) {
            this.reactAgent = reactAgent;
            this.systemPromptBuilder = systemPromptBuilder;
            this.abilityManager = abilityManager;
        }
    }

    private static final class ReactAgentStub {
        private final AgentConfigStub config;

        private ReactAgentStub(AgentConfigStub config) {
            this.config = config;
        }
    }

    private static final class AgentConfigStub {
        private final Map<String, Object> contextProcessors = new LinkedHashMap<>();
        private Object modelConfigObj;
        private Object modelClientConfig;
    }

    private static final class SystemPromptBuilderStub {
        private int addSectionCalls;
        private int removeSectionCalls;

        @SuppressWarnings("unused")
        void addSection(Object section) {
            addSectionCalls++;
        }

        @SuppressWarnings("unused")
        void removeSection(String name) {
            removeSectionCalls++;
        }
    }
}
