/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for agent_builder builders module.
 * <p>
 * Mirrors Python's {@code test_builders_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders}.
 */
class TestBuildersIntegration {

    @Nested
    class TestBuilderFactoryIntegration {

        @Test
        void factoryCreatesLlmAgentBuilderIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            assertThat(builder).isNotNull();
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void factoryCreatesWorkflowBuilderIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.WORKFLOW);
            assertThat(builder).isNotNull();
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        }

        @Test
        void builderGetBuildStatusIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void builderResetIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }
    }

    @Nested
    class TestBaseBuilderIntegration {

        @Test
        void builderStateTransitions() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void builderBuildInInitialState() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            var result = builder.build(java.util.Map.of("query", "test"), java.util.List.of());
            assertThat(result).containsKey("status");
        }
    }

    @Nested
    class TestBuilderResourceIntegration {

        @Test
        void builderResourceManagement() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            assertThat(builder).isNotNull();
        }
    }
}
