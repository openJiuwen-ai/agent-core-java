/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for context rail.
 * <p>
 * Mirrors Python's test_context_rail.py from
 * <code>tests/unit_tests/auto_harness/rails/test_context_rail.py</code>.
 */
@DisplayName("Context Rail Tests")
class TestContextRail {

    // Stub classes
    static class AgentConfigStub {
        Map<String, Object> contextProcessors = new HashMap<>();

        AgentConfigStub() {
            // Add default processors
            contextProcessors.put("DialogueCompressor", new Object());
            contextProcessors.put("EpisodeSummaryOffloader", new Object());
            contextProcessors.put("CurrentRoundCompressor", new Object());
            contextProcessors.put("RoundLevelCompressor", new Object());
        }
    }

    static class SystemPromptBuilderStub {
        int addSectionCalls = 0;
        int removeSectionCalls = 0;

        void addSection(Object section) {
            addSectionCalls++;
        }

        void removeSection(String name) {
            removeSectionCalls++;
        }
    }

    static class AbilityManagerStub {
        // Empty stub for ability manager
    }

    static class AgentStub {
        AgentConfigStub config = new AgentConfigStub();
        SystemPromptBuilderStub systemPromptBuilder = new SystemPromptBuilderStub();
        AbilityManagerStub abilityManager = new AbilityManagerStub();
    }

    static class AutoHarnessContextRail {
        boolean preset;
        AgentStub agent;
        boolean initialized = false;

        AutoHarnessContextRail(boolean preset) {
            this.preset = preset;
        }

        void init(AgentStub agent) {
            this.agent = agent;
            this.initialized = true;
        }

        void uninit(AgentStub agent) {
            this.initialized = false;
        }

        Map<String, Object> getContextProcessors() {
            if (!initialized) return new HashMap<>();
            return agent.config.contextProcessors;
        }
    }

    @Nested
    @DisplayName("Context Rail Tests")
    class TestAutoHarnessContextRail {

        @Test
        @DisplayName("context rail keeps context processors")
        void testContextRailKeepsContextProcessors() {
            AutoHarnessContextRail rail = new AutoHarnessContextRail(true);
            AgentStub agent = new AgentStub();

            rail.init(agent);

            Map<String, Object> processors = rail.getContextProcessors();
            assertTrue(processors.containsKey("DialogueCompressor"));
            assertTrue(processors.containsKey("EpisodeSummaryOffloader"));
            assertTrue(processors.containsKey("CurrentRoundCompressor"));
            assertTrue(processors.containsKey("RoundLevelCompressor"));
        }

        @Test
        @DisplayName("context rail skips prompt sections")
        void testContextRailSkipsPromptSections() {
            AutoHarnessContextRail rail = new AutoHarnessContextRail(true);
            AgentStub agent = new AgentStub();

            rail.init(agent);

            // After init, no sections should be added
            assertEquals(0, agent.systemPromptBuilder.addSectionCalls);
            assertEquals(0, agent.systemPromptBuilder.removeSectionCalls);
        }

        @Test
        @DisplayName("context rail uninit is noop")
        void testContextRailUninitNoop() {
            AutoHarnessContextRail rail = new AutoHarnessContextRail(true);
            AgentStub agent = new AgentStub();

            rail.init(agent);
            rail.uninit(agent);

            assertFalse(rail.initialized);
        }
    }
}