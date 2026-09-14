/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestBaseAgentBuilder} and {@code TestAgentBuilderFactory} tests in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/test_base.py}.
 */
class BaseAgentBuilderPythonParityTest {

    @Test
    void builderCreationInitializesCollaboratorsStateAndResource() {
        Model llm = modelReturning("{\"tool_id_list\": []}");
        HistoryManager historyManager = new HistoryManager();

        ConcreteAgentBuilder builder = new ConcreteAgentBuilder(llm, historyManager);

        assertThat(builder.getLlm()).isSameAs(llm);
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
    }

    @Test
    void statePropertyCanMoveThroughBuildStates() {
        ConcreteAgentBuilder builder = builder();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);

        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);

        builder.setState(AgentBuilderEnums.BuildState.COMPLETED);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.COMPLETED);
    }

    @Test
    void resourcePropertyStoresPluginResourceMap() {
        ConcreteAgentBuilder builder = builder();

        assertThat(builder.getResource()).isEmpty();

        builder.setResource(Map.of("plugins", List.of(Map.of("id", "1"))));

        assertThat(builder.getResource()).containsEntry("plugins", List.of(Map.of("id", "1")));
    }

    @Test
    void executeInitialStateReturnsInitialResultAndMovesToProcessing() {
        ConcreteAgentBuilder builder = builder();

        Object result = builder.execute("test query");

        assertThat(result).isEqualTo("Initial: test query");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
    }

    @Test
    void executeProcessingStateReturnsProcessingResult() {
        ConcreteAgentBuilder builder = builder();
        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);

        Object result = builder.execute("test query");

        assertThat(result).isEqualTo("Processing: test query");
    }

    @Test
    void executeCompletedStateReturnsCompletedMap() {
        ConcreteAgentBuilder builder = builder();
        builder.setState(AgentBuilderEnums.BuildState.COMPLETED);

        Object result = builder.execute("test query");

        assertThat(result).isEqualTo(Map.of("result", "Completed: test query"));
    }

    @Test
    void resetReturnsStateAndResourceToInitialValues() {
        ConcreteAgentBuilder builder = builder();
        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        builder.setResource(Map.of("test", "value"));

        builder.reset();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
        assertThat(builder.internalReset).isTrue();
    }

    @Test
    void getBuildStatusReportsStateAndListResourceCount() {
        ConcreteAgentBuilder builder = builder();
        builder.setResource(Map.of("plugins", List.of(Map.of("id", "1"), Map.of("id", "2"))));

        Map<String, Object> status = builder.getBuildStatus();

        assertThat(status).containsEntry("state", "initial");
        Map<?, ?> resourceCount = (Map<?, ?>) status.get("resource_count");
        assertThat(resourceCount.get("plugins")).isEqualTo(2);
    }

    @Test
    void factoryCreatesLlmAgentBuilder() {
        AgentBuilderFactory.clearRegistry();

        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                modelReturning("{\"tool_id_list\": []}"),
                new HistoryManager()
        );

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
    }

    @Test
    void factoryCreatesWorkflowBuilder() {
        AgentBuilderFactory.clearRegistry();

        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW,
                modelReturning("{\"tool_id_list\": []}"),
                new HistoryManager()
        );

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
    }

    @Test
    void factoryRejectsUnsupportedAgentTypeValue() {
        assertThatThrownBy(() -> AgentBuilderEnums.AgentType.fromValue("unsupported_type"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factorySupportedTypesReturnsList() {
        AgentBuilderFactory.clearRegistry();
        AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                modelReturning("{\"tool_id_list\": []}"),
                new HistoryManager()
        );

        List<AgentBuilderEnums.AgentType> types = AgentBuilderFactory.getSupportedTypes();

        assertThat(types).isInstanceOf(List.class);
        assertThat(types).contains(AgentBuilderEnums.AgentType.LLM_AGENT, AgentBuilderEnums.AgentType.WORKFLOW);
    }

    private static ConcreteAgentBuilder builder() {
        return new ConcreteAgentBuilder(modelReturning("{\"tool_id_list\": []}"), new HistoryManager());
    }

    private static Model modelReturning(String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }

    private static final class ConcreteAgentBuilder extends BaseAgentBuilder {
        private boolean internalReset;

        private ConcreteAgentBuilder(Model llm, HistoryManager historyManager) {
            super(llm, historyManager);
        }

        @Override
        protected Object handleInitial(String query, List<Map<String, String>> dialogHistory) {
            setState(AgentBuilderEnums.BuildState.PROCESSING);
            return "Initial: " + query;
        }

        @Override
        protected Object handleProcessing(String query, List<Map<String, String>> dialogHistory) {
            return "Processing: " + query;
        }

        @Override
        protected Object handleCompleted(String query, List<Map<String, String>> dialogHistory) {
            return Map.of("result", "Completed: " + query);
        }

        @Override
        protected void resetInternalState() {
            internalReset = true;
        }

        @Override
        protected boolean isWorkflowBuilderInternal() {
            return false;
        }
    }
}
