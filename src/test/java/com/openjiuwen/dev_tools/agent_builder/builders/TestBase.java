/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test base builder functionality.
 * <p>
 * Mirrors Python's {@code test_base.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/test_base.py}.
 */
class TestBase {

    @Test
    void testBuilderCreation() {
        Object mockLlm = new Object();
        HistoryManager historyManager = new HistoryManager();

        ConcreteAgentBuilder builder = new ConcreteAgentBuilder(mockLlm, historyManager);

        assertThat(builder.getLlm()).isSameAs(mockLlm);
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
    }

    @Test
    void testStateProperty() {
        ConcreteAgentBuilder builder = newBuilder();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        builder.setState(AgentBuilderEnums.BuildState.COMPLETED);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.COMPLETED);
    }

    @Test
    void testResourceProperty() {
        ConcreteAgentBuilder builder = newBuilder();

        assertThat(builder.getResource()).isEmpty();

        builder.setResource(Map.of("plugins", List.of(Map.of("id", "1"))));

        assertThat(builder.getResource()).containsEntry("plugins", List.of(Map.of("id", "1")));
    }

    @Test
    void testExecuteInitialState() {
        ConcreteAgentBuilder builder = newBuilder();

        Map<String, Object> result = builder.execute("test query");

        assertThat(result).containsEntry("result", "Initial: test query");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
    }

    @Test
    void testExecuteProcessingState() {
        ConcreteAgentBuilder builder = newBuilder();
        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);

        Map<String, Object> result = builder.execute("test query");

        assertThat(result).containsEntry("result", "Processing: test query");
    }

    @Test
    void testExecuteCompletedState() {
        ConcreteAgentBuilder builder = newBuilder();
        builder.setState(AgentBuilderEnums.BuildState.COMPLETED);

        Map<String, Object> result = builder.execute("test query");

        assertThat(result).containsEntry("result", "Completed: test query");
    }

    @Test
    void testReset() {
        ConcreteAgentBuilder builder = newBuilder();
        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        builder.setResource(Map.of("test", "value"));

        builder.reset();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetBuildStatus() {
        ConcreteAgentBuilder builder = newBuilder();
        builder.setResource(Map.of("plugins", List.of(Map.of("id", "1"), Map.of("id", "2"))));

        Map<String, Object> status = builder.getBuildStatus();

        assertThat(status).containsEntry("state", "initial");
        assertThat((Map<String, Object>) status.get("resource_count")).containsEntry("plugins", 2);
    }

    @Test
    void testCreateLlmAgentBuilder() {
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                new Object(),
                new HistoryManager()
        );

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(LlmAgentBuilder.class);
    }

    @Test
    void testCreateWorkflowBuilder() {
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW,
                new Object(),
                new HistoryManager()
        );

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(WorkflowBuilder.class);
    }

    @Test
    void testCreateUnsupportedTypeRaisesError() {
        assertThatThrownBy(() -> AgentBuilderFactory.create(null, new Object(), new HistoryManager()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSupportedTypes() {
        AgentBuilderFactory.clearRegistry();
        assertThat(AgentBuilderFactory.getSupportedTypes()).isEmpty();

        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, new Object(), new HistoryManager());

        assertThat(AgentBuilderFactory.getSupportedTypes())
                .contains(AgentBuilderEnums.AgentType.LLM_AGENT, AgentBuilderEnums.AgentType.WORKFLOW);
    }

    private ConcreteAgentBuilder newBuilder() {
        return new ConcreteAgentBuilder(new Object(), new HistoryManager());
    }

    private static final class ConcreteAgentBuilder extends BaseAgentBuilder {
        private ConcreteAgentBuilder(Object llm, HistoryManager historyManager) {
            super(llm, historyManager, null);
        }

        @Override
        protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
            setState(AgentBuilderEnums.BuildState.PROCESSING);
            return Map.of("result", "Initial: " + query.get("query"));
        }

        @Override
        protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("result", "Processing: " + query.get("query"));
        }

        @Override
        protected Map<String, Object> handleCompleted(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("result", "Completed: " + query.get("query"));
        }
    }
}
