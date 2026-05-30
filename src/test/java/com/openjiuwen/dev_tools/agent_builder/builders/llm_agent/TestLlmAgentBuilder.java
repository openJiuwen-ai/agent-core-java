/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.dev_tools.agent_builder.builders.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test LlmAgentBuilder initialization and properties.
 * <p>
 * Mirrors Python's {@code test_builder.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_builder.py}.
 *
 */
class TestLlmAgentBuilder {

    /**
     * Test LlmAgentBuilder initialization.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);

            assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
            assertNotNull(builder.getProgressReporter());
            assertTrue(builder.getResource().isEmpty());
        }

        @Test
        void testInitProgressReporterDefaultNone() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);

            assertNotNull(builder.getProgressReporter());
        }
    }

    /**
     * Test LlmAgentBuilder resource unique key.
     */
    @Nested
    class TestResourceUniqueKey {

        @Test
        void testResourceUniqueKey() {
            assertEquals("tool_id", LlmAgentBuilder.RESOURCE_UNIQUE_KEY.get("plugins"));
        }
    }

    /**
     * Test LlmAgentBuilder resource property.
     */
    @Nested
    class TestResource {

        @Test
        void testResourceProperty() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);

            assertTrue(builder.getResource().isEmpty());
        }
    }

    /**
     * Test LlmAgentBuilder state property.
     */
    @Nested
    class TestState {

        @Test
        void testStateProperty() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);

            assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            assertEquals(AgentBuilderEnums.BuildState.PROCESSING, builder.getState());
        }
    }
}
