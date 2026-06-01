/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for agent_builder builders module.
 * <p>
 * Mirrors Python's {@code test_builders_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders}.
 */
class TestBuildersIntegration {

    @BeforeEach
    void setUp() {
        AgentBuilderFactory.clearRegistry();
    }

    @AfterEach
    void tearDown() {
        AgentBuilderFactory.clearRegistry();
    }

    @Nested
    class TestBuilderFactoryIntegration {

        @Test
        void factoryCreatesLlmAgentBuilderIntegration() {
            Object mockLlm = new Object();
            HistoryManager historyManager = new HistoryManager();

            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    mockLlm,
                    historyManager
            );

            assertThat(builder).isNotNull();
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void factoryCreatesWorkflowBuilderIntegration() {
            Object mockLlm = new Object();
            HistoryManager historyManager = new HistoryManager();

            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.WORKFLOW,
                    mockLlm,
                    historyManager
            );

            assertThat(builder).isNotNull();
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        }

        @Test
        void builderGetBuildStatusIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    new Object(),
                    new HistoryManager()
            );

            Map<String, Object> status = builder.getBuildStatus();

            assertThat(status).containsEntry("state", AgentBuilderEnums.BuildState.INITIAL.getValue());
        }

        @Test
        void builderResetIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    new Object(),
                    new HistoryManager()
            );
            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            builder.setResource(Map.of("plugins", List.of(Map.of("id", "1"))));
            builder.reset();

            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
            assertThat(builder.getResource()).isEmpty();
        }
    }

    @Nested
    class TestBaseBuilderIntegration {

        @Test
        void builderWithHistoryManager() {
            HistoryManager historyManager = new HistoryManager();

            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    new Object(),
                    historyManager
            );

            assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        }

        @Test
        void builderStateTransitions() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    new Object(),
                    new HistoryManager()
            );
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);

            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);

            builder.setState(AgentBuilderEnums.BuildState.COMPLETED);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.COMPLETED);
        }

        @Test
        void builderBuildInInitialState() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    new Object(),
                    new HistoryManager()
            );
            var result = builder.build(java.util.Map.of("query", "test"), java.util.List.of());
            assertThat(result).containsKey("status");
        }
    }

    @Nested
    class TestBuilderResourceIntegration {

        @Test
        void builderResourceManagement() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    new Object(),
                    new HistoryManager()
            );

            assertThat(builder.getResource()).isEmpty();
        }
    }
}
