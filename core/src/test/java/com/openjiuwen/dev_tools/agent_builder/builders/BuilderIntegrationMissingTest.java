/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's builder integration tests in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/test_builders_integration.py}.
 */
class BuilderIntegrationMissingTest {

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
            HistoryManager historyManager = new HistoryManager();

            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    modelReturning("{}"),
                    historyManager);

            assertThat(builder).isNotNull();
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void factoryCreatesWorkflowBuilderIntegration() {
            HistoryManager historyManager = new HistoryManager();

            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.WORKFLOW,
                    modelReturning("{}"),
                    historyManager);

            assertThat(builder).isNotNull();
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        }

        @Test
        void builderGetBuildStatusIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    modelReturning("{}"),
                    new HistoryManager());

            Map<String, Object> status = builder.getBuildStatus();

            assertThat(status).containsKey("state");
            assertThat(status.get("state")).isEqualTo(AgentBuilderEnums.BuildState.INITIAL.getValue());
        }

        @Test
        void builderResetIntegration() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    modelReturning("{}"),
                    new HistoryManager());

            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            builder.setResource(Map.of("plugins", List.of(Map.of("resource_id", "plugin-1"))));
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
                    modelReturning("{}"),
                    historyManager);

            assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        }

        @Test
        void builderStateTransitions() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    modelReturning("{}"),
                    new HistoryManager());

            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);

            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);

            builder.setState(AgentBuilderEnums.BuildState.COMPLETED);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.COMPLETED);
        }
    }

    @Nested
    class TestBuilderResourceIntegration {

        @Test
        void builderResourceManagement() {
            BaseAgentBuilder builder = AgentBuilderFactory.create(
                    AgentBuilderEnums.AgentType.LLM_AGENT,
                    modelReturning("{}"),
                    new HistoryManager());

            assertThat(builder.getResource()).isInstanceOf(Map.class);
            assertThat(builder.getResource()).isEmpty();
        }
    }

    private static Model modelReturning(String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(response)));
    }
}
